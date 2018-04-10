(ns glickit.core
  (:require [glickit.ranker :refer [vol-calc v E g]]
            [glickit.helpers :refer :all]))

;;; This namespace is an implementation of the Glicko-2 rating system, developed
;;; by Mark Glickman.

;; It is in the public domain. The resources, as well as the document used to
;; implement this, can be found here:
;; http://www.glicko.net/glicko.html.
;; (This document is also included in the "resources" folder of this project.)

;; A note on tau:
; Tau is a measure of how much volatility impacts the final rating.
; For games with more RNG (Hearthstone, etc) this can be turned down to as low
; as 0.3. See the Glicko-2 website for more information.

;; A note on scaling:
; This uses raw Glicko-2 scaling (IE rating is centered on 0, not 1500).
; The rationale for this is that it is easier to convert the raw scaling to
; Glicko-1 scaling only when needed, especially for large sets that cover a large
; time scale. Functions to do this conversion will be provided in a different namespace.

(defn glicko-rank
  "Takes in tau and a set of match data during a rating period for a single player. Returns updated values."
  [tau
   player-rating
   player-dev
   player-vol
   opponent-ratings
   opponent-devs
   outcomes]
  (let [new-vol (vol-calc tau
                          player-rating
                          player-dev
                          player-vol
                          opponent-ratings
                          opponent-devs
                          outcomes)
        temp-dev (Math/hypot player-dev new-vol)
        v (v player-rating opponent-ratings opponent-devs)
        new-dev (/ 1.0 (Math/sqrt
                        (+ (/ 1.0 (Math/pow temp-dev 2.0))
                           (/ 1.0 v))))]
    [(+ player-rating
      (* (Math/pow new-dev 2.0)
         (reduce + (map *
                        (map g opponent-devs)
                        (map - outcomes
                             (map (partial E player-rating) opponent-ratings opponent-devs))))))
     new-dev
     new-vol]))


;; Various helper Functions

;; Glicko-2 ratings start at 0, Glicko-1 ratings start at 1500.
;; Glicko-2 devs start at 2.014761872416068, Glicko-1 devs start at 350.
;; Volatility is Glicko-2 only, and does not need to be converted.

(defn rating1->rating2
  "Takes in a Glicko-1 rating (initial value of 1500) and converts it to a Glicko-2 rating (initial value of 0)."
  [player-rating]
  (/ (- player-rating 1500.0) 173.7178))

(defn dev1->dev2
  "Takes in a Glicko-1 deviation (initial value of 350) and converts it to a Glicko-2 deviation (initial value of 2.014761872416068)."
  [player-dev]
  (/ player-dev 173.7178))

(defn rating2->rating1
  "Takes in a Glicko-2 rating (initial value of 0) and converts it to a Glicko-1 rating (initial value of 1500)."
  [player-rating]
  (+ 1500.0 (* player-rating 173.7178)))

(defn dev2->dev1
  "Takes in a Glicko-2 deviation (initial value of 2.014761872416068) and converts it to a Glicko-1 deviation (initial value of 350)."
  [player-dev]
  (* player-dev 173.7178))

(defn glicko1->glicko2
  "Converts Glicko-1 values to Glicko-2.
  Note that volatility does not change, and is not included."
  [player-rating
   player-dev
   player-vol]
  [(rating1->rating2 player-rating)
   (dev1->dev2 player-dev)
   player-vol])

(defn glicko2->glicko1
  "Converts Glicko-2 values to Glicko-1. Note that volatility does not change, and is not included."
  [player-rating
   player-dev
   player-vol]
  [(rating2->rating1 player-rating)
   (dev2->dev1 player-dev)
   player-vol])

(defn inactive-update
  "Updates a player's deviation after a period of inactivity. This is a multiple-arity function. If called with rating, deviation, and volatility, will return the three values with updated deviation assuming one rating period of inactivity. If called with periods-inactive, rating, deviation, and volatility, will return the three values with updated deviation assuming periods-inactive periods of inactivity."
  ([player-rating player-dev player-vol]
   [player-rating
    (Math/hypot player-dev player-vol)
    player-vol])
  ([periods-inactive player-rating player-dev player-vol]
   [player-rating
    (Math/hypot player-dev (* periods-inactive player-vol))
    player-vol]))
