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
           err :err} (sh "./minimal.clj" :dir "./examples")
          fixture "[36mvero »[0m (git rev-parse --show-toplevel)
minimal.clj

Usage:
  minimal.clj hello

Options:
  -h --help             Show this screen"]
      (is (= 0 exit))
      (is (= (string/trim out) (string/trim fixture))))))

(deftest test-extra-classpath-example
  (testing "Testing examples/extra-classpath.clj"
    (let [{exit :exit
           out :out
           err :err} (sh "./extra-classpath.clj" "example-method" :dir "./examples")
          fixture "[36mvero »[0m (git rev-parse --show-toplevel)
Example method from an extra classpath"]
      (is (= 0 exit))
      (is (= (string/trim out) (string/trim fixture))))))

(run-tests)

;; help emacs understand this is a clojure file
;; Local Variables:
;; mode: clojure
;; End:
