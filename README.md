# Vero

Vero is a utility belt that can be used to build CLI tools using [Babashka](https://github.com/babashka/babashka). With vero we can write performant and flexible Clojure scripts instead of bash scripts. This project is not ready to be used off-the-shelf. Take a look if you're curious about how to use Clojure instead of bash in a variety of different scripting use cases. Also, feel free to contact us to discuss!

Vero is: 

* A library of functions that are useful when writing "bash like" scripts 
* A scaffolding for how to run Babashka scripts with some standardized features on top: argument parsing, config file loading, error handling, etc.

Vero is experimental and not finished. At ekspono we use Vero for all our "scripts". This includes both monorepo utility scripts and cloud infrastructure management scripts. This repo contains the core of Vero. Internally we have a lot of additional libraries that help us solve our use-cases.

## Setup

At the moment Vero is a bit complicated to set up. This is fine for us since we have a monorepo structure and a containerized dev environment where Vero is provided.

## Example

Here's an annotated example of a Vero script that tries to convey the most important features:

**my-example-file.edn**
```Clojure
{:text "This text comes from an edn file"}
```

**example.clj**

(`bin/vero` is a wrapper script that is used to fix argument passing on linux and MacOS when calling the Babashka binary)

```Clojure
#!bin/vero

#!../bin/vero

(require '[ekspono.vero :as vero])

(defn say-hello
  []
  (let [from-opt (vero/var "FROM_CLI_OPTION")
        from-env-var (vero/var "FROM_ENV_VAR")
        from-cmd (vero/var "FROM_CMD")
        from-edn-file (vero/var "FROM_EDN_FILE")]
    (println from-opt)
    (println from-env-var)
    (println from-cmd)
    (println from-edn-file)))

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

```

Execute with: 

`MY_ENV_VAR="This text comes from an env var" ./example.clj say-hello --text text_from_env_var`

Executable example scripts can be found in the `examples` directory.
