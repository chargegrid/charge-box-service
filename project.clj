(defproject charge-box-service "0.1.0-SNAPSHOT"
  :description "Serve charge box data, store static charge box data"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [clj-time "0.11.0"] ; required due to bug in `lein-ring uberwar`
                 [metosin/compojure-api "1.0.2"]
                 [com.novemberain/langohr "3.5.0"]
                 [ring/ring-defaults "0.1.5"]
                 [ring/ring-json "0.4.0"]
                 [ring/ring-devel "1.4.0"]
                 [org.clojure/data.json "0.2.6"]
                 [camel-snake-kebab "0.3.2"]
                 [korma "0.4.2"]
                 [org.postgresql/postgresql "9.4.1207.jre7"]
                 [ragtime "0.5.2"]
                 [org.clojure/tools.logging "0.3.1"]
                 [ch.qos.logback/logback-classic "1.1.3"]
                 [ring.middleware.logger "0.5.0" :exclusions [org.slf4j/slf4j-log4j12]]
                 [http-kit "2.1.18"]
                 [cprop "0.1.9"]
                 [com.grammarly/perseverance "0.1.2"]]

  :profiles {:uberjar {:aot :all}}

  :target-path "target/%s/"

  :main charge-box-service.core
  :uberjar-name "charge-box-service.jar")
