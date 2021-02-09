(ns ekspono.vero.errors
  (:require [ekspono.vero.colors :as colors]
            [ekspono.vero.sh :as vsh]))

(defn fatal! [title description props]
  (let [p (->> (with-out-str (clojure.pprint/pprint props)))
        out (str (colors/red (str "FATAL: " title))
                 "\n\n"
                 (colors/yellow 
                  (str description
                       "\n\n"
                       "context:\n"
                       p)))]
    (print out)
    (vsh/exit 10 "")))
