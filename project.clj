(defproject net.clojars.jtkdvlp/lein-tpl-helpers "1.0.1"
  :description
  "Helpers to work / create leiningen templates"

  :url
  "https://github.com/jtkDvlp/lein-tpl-helpers"

  :license
  {:name "Eclipse Public License"
   :url  "http://www.eclipse.org/legal/epl-v10.html"}

  :eval-in-leiningen
  true

  :dependencies
  [[org.clojure/java.classpath "1.0.0"]]

  :plugins
  [[lein-shell "0.5.0"]
   [lein-ancient "0.6.15"]
   [lein-changelog "0.3.2"]]

  :profiles
  {:dev
   {:dependencies
    [[org.clojure/clojure "1.10.0"]]}}

  :deploy-repositories
  [["releases" :clojars]]

  ;; :aliases
  ;; {"update-readme-version"
  ;;  ["shell" "sed" "-i" "s/\\\\[jtk-dvlp/lein-tpl-helpers \"[0-9.]*\"\\\\]/[jtk-dvlp/lein-tpl-helpers \"${:version}\"]/" "README.md"]}

  ;; :release-tasks
  ;; [["shell" "git" "diff" "--exit-code"]
  ;;  ["change" "version" "leiningen.release/bump-version"]
  ;;  ["change" "version" "leiningen.release/bump-version" "release"]
  ;;  ["changelog" "release"]
  ;;  ["update-readme-version"]
  ;;  ["vcs" "commit"]
  ;;  ["vcs" "tag"]
  ;;  ["deploy"]
  ;;  ["vcs" "push"]]
  )
