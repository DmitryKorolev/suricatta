# suricatta #

[![Travis Badge](https://img.shields.io/travis/funcool/suricatta.svg?style=flat)](https://travis-ci.org/funcool/suricatta "Travis Badge")

High level sql toolkit for clojure (backed by jooq library)

## Latest Version

[![Clojars Project](http://clojars.org/funcool/suricatta/latest-version.svg)](http://clojars.org/funcool/suricatta)


## Quick Start ##

Put suricatta on your dependency list:

```clojure
[funcool/suricatta "0.6.2"]
[com.h2database/h2 "1.4.190"] ;; For this example only
```

Define a valid dbspec hashmap:

```clojure
(def dbspec {:subprotocol "h2"
             :subname "mem:"})
```

Connect to the database and execute a query:

```clojure
(require '[suricatta.core :as sc])

(with-open [ctx (sc/context dbspec)]
  (sc/fetch ctx "select x from system_range(1, 2);"))
;; => [{:x 1} {:x 2}]
```


## Documentation ##

http://funcool.github.io/suricatta/latest/
