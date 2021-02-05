#!/bin/sh

#_(
   "exec" "bb" "--classpath" "src" "$0" "$@"
   )

(require '[ekspono.vero :as vero])

(defn hello
  []
  (println "world!"))

(def usage "minimal.clj

Usage:
  minimal.clj hello

Options:
  -h --help             Show this screen")

(def config
  {:usage usage
   :vars []})

(vero/start config *command-line-args*
            (fn [opts]
              (cond
                (:hello opts) (hello))))
