#!../bin/vero --vero-classpath=extra_classpath/src

(require '[ekspono.vero :as vero]
         '[extra :as extra :refer [example-method]])

(def usage "extra-classpath.clj

Usage:
  extra-classpath.clj example-method

Options:
  -h --help             Show this screen")

(def config
  {:usage usage
   :vars []})

(vero/start config *command-line-args*
            (fn [opts]
              (cond
                (:example-method opts) (example-method))))
