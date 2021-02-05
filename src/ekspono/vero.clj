(ns ekspono.vero
  (:require [clojure.string :as string]
            [clojure.java.io :as io]
            [ekspono.vero.config :as config]
            [ekspono.vero.sh :as vsh]
            [ekspono.vero.docopt-parser :as docopt-parser]))

(defn get-env-vars
  []
  (:vars @config/config+))

(defn exit [status msg]
  (vsh/exit status msg))

(defn run-command [& args]
  (vsh/run-command args))

(defn var
  [var-name]
  (config/var var-name))

(defn set-var
  [var-name val]
  (config/set-var var-name val))

(defn file
  [file-name]
  (config/file file-name))

(defn file-exists?
  [path]
  (.exists (io/file path)))

;; Run a command and capture the resulting status code and output
(defn <-run
  ([cmd opts]
   (vsh/<-run cmd opts))
  ([cmd]
   (<-run cmd {})))

;; Run a command and append the output to a specific log file
(defn run*
  ([log-file cmd opts]
   (vsh/run* log-file cmd opts))
  ([log-file cmd]
   (run* log-file cmd {})))

;; Run a command in interactive mode
(defn run
  ([cmd opts]
   (run* nil cmd (assoc opts :interactive true)))
  ([cmd]
   (run* nil cmd {:interactive true})))

(defn start
  [raw-config args cb]

  ;; Prepare docopt dependency unless provided by the preflight-fn
  (let [repo-dir (->> (<-run ["git" "rev-parse" "--show-toplevel"])
                      (:output)
                      (string/trim)
                      (string/trim-newline))]
    ;; A custom preflight function can be passed as a part of the config
    ;; This can be used to configure repo-level things that need to be in 
    ;; place before vero scripts are executed (creating empty dirs, downloading dependencies, etc)
    (when-let [preflight-fn (:preflight-fn raw-config)]
      (apply preflight-fn [repo-dir]))

    (docopt-parser/init repo-dir run))

  (docopt-parser/parse (:usage raw-config)
                       args
                       (partial config/process-config raw-config cb)))

