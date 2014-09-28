(ns cljooq.dsl-test
  (:require [clojure.test :refer :all]
            [cljooq.core :refer :all]
            [cljooq.dsl :as dsl]
            [cljooq.format :as fmt]
            [jdbc.core :as jdbc]))

(def dbspec {:subprotocol "h2"
             :subname "mem:"})

(deftest rendering-dialect
  (testing "Default dialect."
    (let [q (dsl/select :id :name)]
      (is (= (fmt/get-sql q) "select id, name from dual"))))

  (testing "Specify concrete dialect"
    (let [q (dsl/select :id :name)]
      (is (= (fmt/get-sql q {:dialect :pgsql})
             "select id, name"))))

  (testing "Specify dialect with associated config"
    (with-open [conn (jdbc/make-connection dbspec)]
      (let [ctx (context conn)
            q1   (dsl/select :id :name)
            q2   (query ctx q1)]
        (is (= (fmt/get-sql q2 {:dialect :pgsql})
               "select id, name"))
        (is (= (fmt/get-sql q2)
               "select id, name from dual"))))))

(deftest dsl-basic-tests
  (testing "Simple select clause"
    (let [q   (-> (dsl/select :id :name)
                  (dsl/from :books)
                  (dsl/where ["books.id = ?" 2]))
          sql (fmt/get-sql q)
          bv  (fmt/get-bind-values q)]
      (is (= sql "select id, name from books where (books.id = ?)"))
      (is (= bv [2]))))

  (testing "Field as condition"
    (let [q (-> (dsl/select (dsl/field "foo > 5" :alias "bar"))
                (dsl/from "baz"))]
      (is (= (fmt/get-sql q)
             "select foo > 5 \"bar\" from baz"))))

  (testing "Common table expressions"
    (let [cte1 (-> (dsl/name :t1)
                   (dsl/with-fields :f1 :f2)
                   (dsl/as (dsl/select (dsl/val 1) (dsl/val "a"))))
          cte2 (-> (dsl/name :t2)
                   (dsl/with-fields :f1 :f2)
                   (dsl/as (dsl/select (dsl/val 2) (dsl/val "b"))))
          q1   (-> (dsl/with cte1 cte2)
                   (dsl/select (dsl/field "t1.f2"))
                   (dsl/from :t1 :t2))

          ;; Same as previous code but less verbose.
          q    (-> (dsl/with
                    (-> (dsl/name :t1)
                        (dsl/with-fields :f1 :f2)
                        (dsl/as (dsl/select (dsl/val 1) (dsl/val "a"))))
                    (-> (dsl/name :t2)
                        (dsl/with-fields :f1 :f2)
                        (dsl/as (dsl/select (dsl/val 2) (dsl/val "b")))))
                   (dsl/select (dsl/field "t1.f2"))
                   (dsl/from :t1 :t2))
          sql  (fmt/get-sql q {:type :inlined :dialect :pgsql})
          esql (str "with \"t1\"(\"f1\", \"f2\") as (select 1, 'a'), "
                    "\"t2\"(\"f1\", \"f2\") as (select 2, 'b') "
                    "select t1.f2 from t1, t2")]
      (is (= sql esql))))
)

