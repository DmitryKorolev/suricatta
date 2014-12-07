(ns suricatta.extend-test
  (:require [clojure.test :refer :all]
            [suricatta.core :as sc]
            [suricatta.dsl :as dsl]
            [suricatta.proto :as proto]
            [suricatta.format :refer [get-sql get-bind-values sqlvec] :as fmt]
            [cheshire.core :refer :all])
  (:import org.postgresql.util.PGobject
           org.jooq.RenderContext
           org.jooq.BindContext
           org.jooq.impl.DSL))

(def dbspec {:subprotocol "postgresql"
             :subname "//127.0.0.1/test"})

(def ^:dynamic *ctx*)

(defn my-fixture
  [end]
  (with-open [ctx (sc/context dbspec)]
    (sc/atomic ctx
      (binding [*ctx* ctx]
        (end)
        (sc/set-rollback! ctx)))))

(use-fixtures :each my-fixture)

;; (deftest inserting-json
;;   (sc/execute *ctx* "create table t1 (data json, k int)")
;;   (sc/execute *ctx* ["insert into t1 (data, k) values (?, ?)" (DSL/val #{:foo "bar"} datatype) 3])
;;   (-> (sc/fetch *ctx* ["select * from t1"])
;;       (println)))

(deftype MyJson [data])

(defn myjson
  [data]
  (MyJson. data))

(extend-protocol proto/IParamType
  MyJson
  (render [self]
    (str "'"
         (generate-string (.-data self))
         "'::json"))
  (bind [self stmt index]
    (let [obj (doto (PGobject.)
                (.setType "json")
                (.setValue (generate-string (.-data self))))]
      (.setObject stmt index obj))))

(extend-protocol proto/ISQLType
  PGobject
  (convert [self]
    (let [type (.getType self)]
      (condp = type
        "json" (parse-string (.getValue self) true)))))

(deftest inserting-json
  (sc/execute *ctx* "create table t1 (k json)")
  (sc/execute *ctx* ["insert into t1 (k) values (?)" (myjson {:foo 1})])
  (let [result (sc/fetch *ctx* ["select * from t1"])
        result1 (first result)]
    (is (= (:k result1) {:foo 1}))))

(deftest render-json
  (let [q (-> (dsl/insert-into :t1)
              (dsl/insert-values {:data (myjson {:foo 1})}))]
    (is (= (fmt/get-sql q {:dialect :pgsql :type :inlined})
           "insert into t1 (data) values ('{\"foo\":1}'::json)"))))

(deftype MyArray [data])

(defn myintarray
  [data]
  (MyArray. data))

(extend-protocol proto/IParamType
  MyArray
  (render [self]
    (let [items (->> (map str (.-data self))
                     (interpose ","))]
      (str "{" (apply str items) "}")))

  (bind [self stmt index]
    (let [con (.getConnection stmt)
          arr (into-array Long (.-data self))
          arr (.createArrayOf con "bigint" arr)]
      (.setArray stmt index arr))))

(extend-protocol proto/ISQLType
  java.sql.Array
  (convert [self]
    (into [] (.getArray self))))

(deftest inserting-arrays
  (sc/execute *ctx* "create table t1 (data bigint[])")
  (let [data (myintarray [1 2 3])]
    (sc/execute *ctx* ["insert into t1 (data) values (?)" data]))
  (let [result (sc/fetch *ctx* "select * from t1")]
    (is (= result [{:data [1 2 3]}]))))
