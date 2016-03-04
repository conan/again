(defproject listora/again "0.1.1-SNAPSHOT"
  :description "A Clojure library for retrying operations."
  :url "https://github.com/listora/again"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :scm {:name "git"
        :url  "https://github.com/listora/again"}
  :deploy-repositories [["releases" :clojars]]
  :profiles {:dev {:dependencies [[org.clojure/core.async "0.2.374"]
                                  [org.clojure/clojure "1.7.0"]
                                  [org.clojure/clojurescript "1.7.228" :scope "provided"]
                                  [org.clojure/test.check "0.5.9"]]}})
