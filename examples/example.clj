#!../bin/vero

(require '[ekspono.vero :as vero :refer [run <-run]]
         '[ekspono.vero.errors :refer [fatal!]])

(defn say-hello
  []
  (let [from-opt (vero/var "FROM_CLI_OPTION")
        from-env-var (vero/var "FROM_ENV_VAR")
        from-cmd (vero/var "FROM_CMD")
        from-edn-file (vero/var "FROM_EDN_FILE")]
    (println from-opt)
    (println from-env-var)
    (println from-cmd)
    (println from-edn-file))
  
  ;; Vero has utility functions for calling other binaries.
  (let [result (<-run ["echo" "hello"])]
    (println "Run commands and capture the exit status and output:" result))
  
  ;; Run commands without capturing the output
  (run ["echo" "some text"]))

;; Argument parsing is done by docopt
(def usage "example.clj

Usage:
  example.clj say-hello --text=<text>

Options:
  --text=<text>         Text input to print
  -h --help             Show this screen")

;; Configuration for Vero
(def config
  {:usage usage
   ;; edn files can be read at startup and are available when Vero vars are created
   :edn-files {:example "my-example-file.edn"}
   ;; Vero variables are created at startup and can be read / written to from any function. 
   ;; They can be created from different sources.
   :vars ["FROM_CLI_OPTION" [:opt :text]
          "FROM_ENV_VAR" [:cmd "echo $MY_ENV_VAR"]
          "FROM_CMD" [:cmd "echo This text comes from a command execution"]
          "FROM_EDN_FILE" [:file [:example :text]]]})

(vero/start config *command-line-args*
            (fn [opts]
              (cond
                (:say-hello opts) (say-hello))))