(ns ekspono.vero
  (:require [clojure.tools.cli :refer [parse-opts]]
            [clojure.java.shell :refer [sh]]
            [clojure.string :as string]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [ekspono.vero.docopt-parser :as docopt-parser]))

(def config (atom {}))

(def alphanumeric "0123456789abcdefghijklmnopqrstuvwxyz")

(defn rand-alphanumeric
  [len]
  (apply str (take len (repeatedly #(rand-nth alphanumeric)))))

(defn get-env-vars
  []
  (:vars @config))

(defn exit [status msg]
  (println msg)
  (System/exit status))

(defn run-command [& args]
  (apply sh "bash" "-c" args))

(defn var
  [var-name]
  (get-in @config [:vars var-name]))

(defn set-var
  [var-name val]
  (swap! config assoc-in [:vars var-name] val))

(defn file
  [file-name]
  (get-in @config [:files file-name]))

(defn file-exists?
  [path]
  (.exists (io/file path)))

(defn- load-env-file
  [f]
  (let [contents (slurp f)]
    (->> (string/split contents #"\n")
         (filter (fn [s] (not (re-matches #"#.*" s))))
         (map (fn [s] (as-> s $
                        (clojure.string/replace $ #"export\s" "")
                        (string/trim $)
                        (string/split $ #"="))))
         (remove (fn [v] (not (= (count v) 2))))
         (into {}))))

(defn- run-with-env
  [cmd opts]
  (println "vero/running: " cmd)
  (let [dir (or (:dir opts) ".")
        env-vars (->> (for [[k v] (:env opts)]
                        (str k "=" v))
                      (into []))
        cmd (concat ["env"] env-vars cmd)
        pb (doto (ProcessBuilder. cmd)
             (.redirectErrorStream true)
             (.directory (clojure.java.io/file dir)))]
    (when (:interactive opts)
      (.inheritIO pb))
    (.start pb)))

(defn <-run
  ([cmd opts]
   (let [cmd-str (map (fn [c] (str c)) cmd)
         process (run-with-env cmd-str opts)
         str-builder (StringBuilder.)]
     (with-open [rdr (clojure.java.io/reader (.getInputStream process))]
       (doseq [line (line-seq rdr)]
         (.append str-builder (str line "\n")))
       (.waitFor process))
     {:status (.exitValue process) :output (.toString str-builder)}))
  ([cmd]
   (<-run cmd {})))

(defn run*
  ([log-file cmd opts]
   (let [cmd-str (map (fn [c] (str c)) cmd)
         expected-exit-code (:expected-exit-code opts 0)
         process (run-with-env cmd-str opts)]
     (with-open [rdr (clojure.java.io/reader (.getInputStream process))]
       (doseq [line (line-seq rdr)]
         (if (not (nil? log-file))
           (spit log-file (str line "\n") :append true)
           (println (str line))))
       (.waitFor process))
     (let [exit-code (.exitValue process)]
       (when-not (:ignore-exit-code opts)
         (when-not (= exit-code expected-exit-code) 
           (println (str "FATAL: Unexpected exit-code from command: " exit-code " (expected exit-code: " expected-exit-code ")"))
           (System/exit exit-code))))))
  ([log-file cmd]
   (run* log-file cmd {})))

(defn run
  ([cmd opts]
   (run* nil cmd (assoc opts :interactive true)))
  ([cmd]
   (run* nil cmd {:interactive true})))

(defn berglas-access
  [url]
  (let [res (<-run ["scripts/secrets.sh" "access-url" url])]
    (if (= (:status res) 0)
      (->> (:output res)
           (string/trim))
      (throw (ex-info (str "ERROR: Command failed while loading config: " (:output res)) {})))))

(defn replace-several [s replacements]
  (reduce (fn [s [match replacement]]
            (clojure.string/replace s match replacement))
          s replacements))

(defn subst-vars
  [s vars]
  (->> (replace-several
        s
        (->> (for [[var-name exp] vars]
               (if (string? exp)
                 [(str "${" var-name "}") exp]
                 nil))
             (remove nil?)))
       (string/trim)
       (string/trim-newline)))

(defn subst
  [s]
  (subst-vars s (:vars @config)))

(defn process-directive
  [var-name exp selected-exp-type exp-fn vars]
  (let [exp-type (first exp)
        exp-content (second exp)
        exp-default-val (nth exp 2 nil)]
    (if (= selected-exp-type exp-type)
      (exp-fn var-name exp-content exp-default-val vars)
      [var-name exp])))

(defn process-config-var
  [var-entry selected-exp-type exp-fn vars]
  (let [var-name (first var-entry)
        exp (second var-entry)]
    (cond (vector? exp) (process-directive var-name exp selected-exp-type exp-fn vars)
          (string? exp) [var-name (subst-vars exp vars)]
          (boolean? exp) [var-name exp])))

(defn process-config-vars
  ([vars processed-vars selected-exp-type exp-fn]
   (if (seq vars)
     (let [var-entry (first vars)
           processed-var (process-config-var var-entry 
                                             selected-exp-type 
                                             exp-fn 
                                             (merge (->> vars (into {})) 
                                                    (->> processed-vars (into {}))))]
       (recur (rest vars)
              (conj processed-vars processed-var)
              selected-exp-type
              exp-fn))
     processed-vars))
  ([vars selected-exp-type exp-fn]
   (process-config-vars
    vars
    []
    selected-exp-type
    exp-fn)))

(defn read-files
  [files current-vars]
  (->> (for [[k path] files]
         (let [subst-path (subst-vars path current-vars)]
           (try
             (let [contents (slurp subst-path)]
               [k (edn/read-string contents)])
             (catch Exception e
               (println "Warning: file not found: " subst-path)
               [k nil]))))
       (into {})))

(defn read-config
  [raw-config options]
  (let [raw-vars (:vars raw-config)
        vars-tuples (->> raw-vars
                         (partition 2)
                         (map #(into [] %)))
        vars-opts (process-config-vars
                   vars-tuples
                   :opt
                   (fn [var-name exp-content exp-default-val current-vars]
                     (if-let [val (get options exp-content)]
                       [var-name val]
                       (if (some? exp-default-val)
                         [var-name exp-default-val]
                         [var-name nil]))))
        vars-cmd (process-config-vars
                  vars-opts
                  :cmd
                  (fn [var-name exp-content exp-default-val current-vars]
                    (let [subst-exp-content (subst-vars exp-content current-vars)
                          result (run-command subst-exp-content)
                          err (:err result)]
                      (if (seq err)
                        (throw (ex-info (str "ERROR: Command failed while loading config: " err) {}))
                        [var-name (->> (:out result)
                                       (string/trim)
                                       (string/trim-newline))]))))
        vars-berglas (process-config-vars
                      vars-cmd
                      :berglas
                      (fn [var-name exp-content exp-default-val current-vars]
                        (let [secret (berglas-access exp-content)]
                          [var-name secret])))
        files (read-files (:edn-files raw-config) vars-berglas)
        vars-files (process-config-vars
                    vars-berglas
                    :file
                    (fn [var-name exp-content exp-default-val current-vars]
                      (let [file-id (first exp-content)
                            query (->> (rest exp-content)
                                       (map (fn [c]
                                              (if (string? c)
                                                (subst-vars c current-vars)
                                                c)))
                                       (map (fn [c]
                                              (if (and (string? c)
                                                       (clojure.string/starts-with? c ":"))
                                                (keyword (subs c 1))
                                                c)))
                                       (into []))
                            file (get files file-id)]
                        (if-not (nil? file)
                          (if-let [result (get-in file query)]
                            [var-name result]
                            (if-not (nil? exp-default-val)
                              [var-name exp-default-val]
                              (throw (ex-info (str "ERROR: Invalid file/query and no default value: " file-id " " query) {}))))
                          (if-not (nil? exp-default-val)
                            [var-name exp-default-val]
                            (throw (ex-info (str "ERROR: Invalid file/query and no default value: " file-id " " query) {})))))))
        vars-str (process-config-vars
                  vars-files
                  :string
                  (fn [var-name exp-content exp-default-val current-vars]
                    (let [subst-exp-content (subst-vars exp-content current-vars)]
                      [var-name subst-exp-content])))]
    (-> raw-config 
        (assoc :vars (->> vars-str
                          (into {})))
        (assoc :files files))))

(defn run-parallel
  [fn-sets]
  (let [results (for [fns fn-sets]
                  (doall (pmap (fn [f] (apply f [])) fns)))]
    (flatten results)))

(defn process-config
  [raw-config cb options]

  (let [c (read-config raw-config options)]
    (reset! config c)
    (cb options)))

(defn start
  [raw-config args cb]
  ;; Prepare docopt dependency
  (let [repo-dir (->> (<-run ["git" "rev-parse" "--show-toplevel"])
                      (:output)
                      (string/trim)
                      (string/trim-newline))]
    (docopt-parser/init repo-dir run))

  (docopt-parser/parse (:usage raw-config)
                       args
                       (partial process-config raw-config cb)))

