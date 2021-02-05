#!/bin/sh

#_(
   "exec" "bb" "$0" "$@"
   )
  
(ns vero-test
  (:require [clojure.test :refer [deftest is testing use-fixtures run-tests]]
            [clojure.java.shell :refer [sh]]
            [clojure.java.io :as io]
            [clojure.string :as string]))
  
(deftest test-minimal-example
  (testing "Testing examples/minimal.clj"
    (let [{exit :exit
           out :out
           err :err} (sh "./examples/minimal.clj")
          fixture "vero/running:  (git rev-parse --show-toplevel)
minimal.clj

Usage:
  minimal.clj hello

Options:
  -h --help             Show this screen"]
      (is (= 0 exit))
      (is (= (string/trim out) (string/trim fixture))))))

(run-tests)

;; help emacs understand this is a clojure file
;; Local Variables:
;; mode: clojure
;; End:
