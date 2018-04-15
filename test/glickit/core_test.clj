(ns glickit.core-test
  (:require [clojure.test :refer :all]
            [glickit.core :refer [glicko-rank glicko2->glicko1 rating1->rating2 dev1->dev2 inactive-update]]))


(def test-tolerance 0.01)

(def test-player
  {:player-rating 1500.0
   :player-dev 200.0
   :player-vol 0.06
   :opponent-ratings [1400.0 1550.0 1700.0]
   :opponent-devs [30.0 100.0 300.0]
   :outcomes [1.0 0.0 0.0]})

(def example-results
  {:example-rating 1464.06
   :example-dev 151.52
   :example-vol 0.05999})

(def tau 0.5)

(defn rate
  [{:keys [player-rating
           player-dev
           player-vol
           opponent-ratings
           opponent-devs
           outcomes]}]
  (let [converted-rating (rating1->rating2 player-rating)
        converted-dev (dev1->dev2 player-dev)
        converted-opponent-ratings (map #(rating1->rating2 %) opponent-ratings)
        converted-opponent-devs (map #(dev1->dev2 %) opponent-devs)]
      (if (and (empty? opponent-ratings)
               (empty? opponent-devs)
               (empty? outcomes))
        (let [[new-score new-dev new-vol] (apply glicko2->glicko1 (inactive-update converted-rating converted-dev player-vol))]
          {:player-rating new-score
            :player-dev new-dev
            :player-vol new-vol})
        (let [[new-score new-dev new-vol] (apply glicko2->glicko1 ((partial glicko-rank tau) converted-rating converted-dev player-vol converted-opponent-ratings converted-opponent-devs outcomes))]
          {:player-rating new-score
            :player-dev new-dev
            :player-vol new-vol}))))


(deftest glicko-example
  (let [{:keys [player-rating player-dev player-vol]} (rate test-player)
        {:keys [example-rating example-dev example-vol]} example-results]
    (println "\n New Rating:" player-rating "New Dev:" player-dev "New Vol:" player-vol)
    (println " Expected Rating:" example-rating "Expected Dev:" example-dev "Expected Vol:" example-vol)
    (println " Rating Diff:" (Math/abs (- example-rating player-rating))
             "Dev diff:" (Math/abs (- example-dev player-dev))
             "Vol diff:" (Math/abs (- example-vol player-vol)))
    (is (> test-tolerance (Math/abs (- example-rating player-rating))) "Rating error is less than 0.01")
    (is (> test-tolerance (Math/abs (- example-dev player-dev))) "Dev error is less than 0.01")
    (is (> test-tolerance (Math/abs (- example-vol player-vol))) "Vol error is less than 0.01")))
