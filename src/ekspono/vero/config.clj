(ns ekspono.vero.config
  (:require [clojure.string :as string]
            [clojure.edn :as edn]
            [ekspono.vero.sh :as vsh :refer [<-run run-command]]))

(def config+ (atom {}))

(defn var
  [var-name]
  (get-in @config+ [:vars var-name]))

(defn set-var
  [var-name val]
  (swap! config+ assoc-in [:vars var-name] val))

(defn file
  [file-name]
  (get-in @config+ [:files file-name]))

(defn berglas-access
  [url]
  (let [res (<-run ["berglas" "access" url] {})]
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
  (subst-vars s (:vars @config+)))

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

(defn process-config
  [raw-config cb options]

  (let [c (read-config raw-config options)]
    (reset! config+ c)
    (cb options)))

