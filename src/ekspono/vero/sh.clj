(ns ekspono.vero.sh
  (:require [clojure.tools.cli :refer [parse-opts]]
            [clojure.java.shell :refer [sh]]
            [clojure.string :as string]
            [clojure.edn :as edn]
            [clojure.java.io :as io]))

(defn exit [status msg]
  (println msg)
  (System/exit status))

(defn run-command [& args]
  (apply sh "bash" "-c" args))

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
  [cmd opts]
   (let [cmd-str (map (fn [c] (str c)) cmd)
         process (run-with-env cmd-str opts)
         str-builder (StringBuilder.)]
     (with-open [rdr (clojure.java.io/reader (.getInputStream process))]
       (doseq [line (line-seq rdr)]
         (.append str-builder (str line "\n")))
       (.waitFor process))
     {:status (.exitValue process) :output (.toString str-builder)}))

(defn run*
  [log-file cmd opts]
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

(defn run-parallel
  [fn-sets]
  (let [results (for [fns fn-sets]
                  (doall (pmap (fn [f] (apply f [])) fns)))]
    (flatten results)))