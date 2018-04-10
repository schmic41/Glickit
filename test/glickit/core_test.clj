(ns glickit.core-test
  (:require [clojure.test :refer :all]
            [glickit.core :refer :all]))

(def player-1
  {:player-rating 0.0
   :player-dev 1.1513
   :player-vol 0.06
   :opponent-rating-list [-0.5756 0.2878 1.1513]
   :opponent-dev-list [0.1727 0.5756 1.7269]
   :outcome-list [1 0 0]})

(def tau 0.5)

(println (glicko-rank tau player-1))
