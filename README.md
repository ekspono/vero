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

```Clojure
#!../bin/vero

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

```

Executable example scripts can be found in the `examples` directory.
