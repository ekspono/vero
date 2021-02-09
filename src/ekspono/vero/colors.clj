(ns ekspono.vero.colors
  (:require [clojure.string :as string]))

(defn escape
  [n]
  (str "\033[" n "m"))

(defn color-fn
  [escape-code s]
  (str escape-code s (escape 0)))

(defn grey
  [s]
  (color-fn (escape 30) s))

(defn red
  [s]
  (color-fn (escape 31) s))

(defn green
  [s]
  (color-fn (escape 32) s))

(defn yellow
  [s]
  (color-fn (escape 33) s))

(defn blue
  [s]
  (color-fn (escape 34) s))

(defn magenta
  [s]
  (color-fn (escape 35) s))

(defn cyan
  [s]
  (color-fn (escape 36) s))

(defn white
  [s]
  (color-fn (escape 37) s))


(defn on-grey
  [s]
  (color-fn (escape 40) s))

(defn on-red
  [s]
  (color-fn (escape 41) s))

(defn on-green
  [s]
  (color-fn (escape 42) s))

(defn on-yellow
  [s]
  (color-fn (escape 43) s))

(defn on-blue
  [s]
  (color-fn (escape 44) s))

(defn on-magenta
  [s]
  (color-fn (escape 45) s))

(defn on-cyan
  [s]
  (color-fn (escape 46) s))

(defn on-white
  [s]
  (color-fn (escape 47) s))


(defn bold
  [s]
  (color-fn (escape 1) s))

(defn dark
  [s]
  (color-fn (escape 2) s))

(defn underline
  [s]
  (color-fn (escape 4) s))

(defn blink
  [s]
  (color-fn (escape 5) s))

(defn reverse-color
  [s]
  (color-fn (escape 7) s))

(defn concealed
  [s]
  (color-fn (escape 8) s))