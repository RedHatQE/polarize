(defproject com.github.redhatqe/polarize "0.8.3-SNAPSHOT"
  :description "Polarion related stuff for RHSM TestNG."
  :url "https://github.com/RedHatQE/polarize"
  :java-source-path "src"
  :java-source-paths ["src"]
  :dependencies [[net.sf.jopt-simple/jopt-simple "5.0-beta-1"]
                 [org.reflections/reflections "0.9.10"]
		 [org.testng/testng "6.9.10"]
		 [com.google.code.gson/gson "2.6.2"]
		 [org.slf4j/slf4j-api "1.7.21"]
		 [ch.qos.logback/logback-classic "1.1.7"]
		 [org.apache.httpcomponents/httpclient "4.5.2"]
		 [org.apache.httpcomponents/httpmime "4.5.2"]
		 [org.json/json "20140107"]
		 [com.fasterxml.jackson.core/jackson-core "2.8.2"]
		 [com.fasterxml.jackson.core/jackson-databind "2.8.2"]
		 [org.apache.activemq/activemq-all "5.14.1"]
		 [jakarta.xml.bind/jakarta.xml.bind-api "3.0.0"]]
  :javac-options {:debug "on"}
  :plugins [[lein2-eclipse "2.0.0"]])
