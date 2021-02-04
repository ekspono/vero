(ns ekspono.vero.docopt-parser
  (:require [clojure.java.io :as io]
            [clojure.java.shell :refer [sh]]
            [clojure.string :as string]
            [babashka.classpath :refer [add-classpath]]))

(def download-docopt-cmd
  "#!/bin/bash
   
RESULT=$(clojure -Spath -Sdeps '{:deps {docopt/docopt {:git/url \"https://github.com/nubank/docopt.clj\"
                                 :sha     \"12b997548381b607ddb246e4f4c54c01906e70aa\"}}}')
   
echo $RESULT > $1/vero-classpath")

(defn init
  [repo-dir run]
  (let [vero-dir (str repo-dir "/.vero")]
    (when-not (.exists (io/file (str vero-dir "/vero-classpath")))
      (println "Downloading vero dependency: docopt")
      (run ["mkdir" "-p" vero-dir])
      (spit (str vero-dir "/download-vero-deps.sh") download-docopt-cmd)
      (run ["chmod" "+x" (str vero-dir "/download-vero-deps.sh")])
      (run [(str vero-dir "/download-vero-deps.sh") vero-dir]))
    (let [cp (-> (:out (sh "cat" (str vero-dir "/vero-classpath"))))]
      (add-classpath cp)
      (require '[docopt.core :as docopt]))))

(defn- rename-key
  [key]
  (-> key
      (string/replace-first #"^--" "")
      (string/replace-first #"^-" "")
      (keyword)))

(defn- parse-options
  [arg-map]
  (->> (for [[key val] arg-map]
         [(rename-key key) val])
       (into {})))

(defn parse
  [usage args cb]
  (let [docopt (resolve 'docopt/docopt)]
    (docopt usage
            args
            (fn [arg-map]
              (let [options (parse-options arg-map)]
                (if (:help options)
                  (println usage)
                  (cb options)))))))