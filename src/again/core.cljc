(ns again.core)

(defn constant-strategy
  "Generates a retry strategy with a constant delay (ms) between
  retries, ie the delay is the same for each retry."
  [delay]
  {:pre [(>= delay 0)]}
  (repeat delay))

(defn immediate-strategy
  "Returns a retry strategy that retries without any delay."
  []
  (constant-strategy 0))

(defn additive-strategy
  "Returns a retry strategy where, after the `initial-delay` (ms), the
  delay increases by `increment` (ms) after each retry. The single
  argument version uses the given increment as both the initial delay
  and the increment."
  ([increment]
     (additive-strategy increment increment))
  ([initial-delay increment]
     {:pre [(>= initial-delay 0)
            (>= increment 0)]}
     (iterate #(+ increment %) #?(:clj  (bigint initial-delay)
                                  :cljs initial-delay))))

(defn stop-strategy
  "A no-retries policy."
  []
  nil)

(defn multiplicative-strategy
  "Returns a retry strategy with exponentially increasing delays, ie
  each previous delay is multiplied by delay-multiplier to generate
  the next delay."
  [initial-delay delay-multiplier]
  {:pre [(<= 0 initial-delay)
         (<= 0 delay-multiplier)]}
  (iterate #(* delay-multiplier %) #?(:clj  (bigint initial-delay)
                                      :cljs initial-delay)))

(defn- randomize-delay
  "Returns a random delay from the range [`delay` - `delta`, `delay` + `delta`],
  where `delta` is (`rand-factor` * `delay`). Note: return values are
  rounded to whole numbers, so eg (randomize-delay 0.8 1) can return
  0, 1, or 2."
  [rand-factor delay]
  {:pre [(< 0 rand-factor 1)]}
  (let [delta (* delay rand-factor)
        min-delay (- delay delta)
        max-delay (+ delay delta)
        randomised-delay (+ min-delay (* (rand) (inc (- max-delay min-delay))))]
    ;; The inc is there so that if min-delay is 1 and max-delay is 3,
    ;; then we want a 1/3 chance for selecting 1, 2, or 3.
    ;; Cast the delay to an int.
    #?(:clj  (bigint randomised-delay)
       :cljs randomised-delay)))

(defn randomize-strategy
  "Returns a new strategy where all the delays have been scaled by a
  random number between [1 - rand-factor, 1 + rand-factor].
  Rand-factor must be greater than 0 and less than 1."
  [rand-factor retry-strategy]
  {:pre [(< 0 rand-factor 1)]}
  (map #(randomize-delay rand-factor %) retry-strategy))

(defn max-retries
  "Stop retrying after `n` retries."
  [n retry-strategy]
  {:pre [(>= n 0)]}
  (take n retry-strategy))

(defn clamp-delay
  "Replace delays in the strategy that are larger than `delay` with
  `delay`."
  [delay retry-strategy]
  {:pre [(>= delay 0)]}
  (map #(min delay %) retry-strategy))

(defn max-delay
  "Stop retrying once the a delay is larger than `delay`."
  [delay retry-strategy]
  {:pre [(>= delay 0)]}
  (take-while #(< % delay) retry-strategy))

(defn max-duration
  "Limit the maximum wallclock time of the operation to `timeout` (ms)"
  [timeout retry-strategy]
  (when (and (pos? timeout) (seq retry-strategy))
    (let [[f & r] retry-strategy]
      (cons f
            (lazy-seq (max-duration (- timeout f) r))))))

(defn- sleep
  [delay]
  #?(:clj (Thread/sleep (long delay))))


(defn with-retries*
  [strategy f]
  (if-let [[res] (try
                   [(f)]
                   (catch #?(:clj  Exception
                             :cljs :default) e
                     (when-not (seq strategy)
                       (throw e))))]
    res
    (let [[delay & strategy] strategy]
      #?(:clj  (do (sleep delay)
                   (recur strategy f))
         :cljs (js/setTimeout #('(recur strategy f)) delay)))))

(defmacro with-retries
  "Try executing `body`. If `body` throws an Exception, retry
  according to the retry `strategy`.

  A retry `strategy` is a seq of delays: `with-retries` will sleep the
  duration of the delay (in ms) between each retry. The total number
  of tries is the number of elements in the `strategy` plus one. A
  simple retry stategy would be: [100 100 100 100] which results in
  the operation being retried four times (for a total of five tries)
  with 100ms sleeps in between tries. Note: that infinite strategies
  are supported, but maybe not encouraged...

  Strategies can be built with the provided builder fns, eg
  `linear-strategy`, but you can also create any custom seq of
  delays that suits your use case."
  [strategy & body]
  `(with-retries* ~strategy (fn [] ~@body)))
