(ns glickit.ranker)

(def convergence-tolerance 0.000001)

(defn g
  "A helper function for v."
  [rating-dev]
  (/ 1.0
     (Math/sqrt (+ 1.0
                   (/ (* 3.0
                         (Math/pow rating-dev 2))
                      (* Math/PI Math/PI))))))

(defn E
  "A helper function for v."
  [player-rating opponent-rating opponent-dev]
  (/ 1.0
     (+ 1.0
        (Math/exp (* (* -1.0
                        (g opponent-dev))
                     (- player-rating opponent-rating))))))

(defn v
  "Estimated variance of player's rating based on outcomes only."
  [player-rating opponent-rating opponent-dev]
  (/ 1.0
     (reduce +
             (map *
                  (map #(Math/pow % 2) (map g opponent-dev))
                  (map (partial E player-rating) opponent-rating opponent-dev)
                  (map #(- 1.0 %) (map (partial E player-rating) opponent-rating opponent-dev))))))

(defn delta
  "Estimated improvement in rating based on outcomes only."
  [player-rating opponent-rating opponent-dev outcomes]
  (* (v player-rating opponent-rating opponent-dev)
     (reduce + (map * (map g opponent-dev)
                    (map - outcomes
                         (map (partial E player-rating) opponent-rating opponent-dev))))))

(defn vol-calc
  "Determines new value of volatility."
  [tau player-rating player-dev player-vol opponent-ratings opponent-devs outcomes]
  ; f is a helper function that's used several times in these calculations.
  ; It's cleaner to capture some of the values here than to define a separate function.
  (let [v (v player-rating opponent-ratings opponent-devs)
        delta (delta player-rating opponent-ratings opponent-devs outcomes)
        a (Math/log (Math/pow player-vol 2.0))
        f (fn [x]
            (- (/ (* (Math/exp x)
                     (- (Math/pow delta 2.0)
                        (Math/pow player-dev 2.0)
                        v
                        (Math/exp x)))
                  (* 2.0
                     (Math/pow (+ (Math/pow player-dev 2.0)
                                  v
                                  (Math/exp x))
                               2.0)))
               (/ (- x a)
                  tau)))]
    ; Loop/recur is necessary here to implement the two iterative procedures.
    ; Type hinting necessary to remove auto-boxing.
    (loop [A ^Double (Math/log (Math/pow player-vol 2.0))
           B (if (> (Math/pow delta 2.0) (+ (Math/pow player-dev 2.0) v))
               (Math/log (- (Math/pow delta 2.0) (Math/pow player-dev 2.0) v))
               (loop [k 1.0]
                 (if (pos? (f (- a (* k tau))))
                   (- a (* k tau))
                   (recur (inc k)))))
           fA (f A)
           fB (f B)]
      (if (< (Math/abs ^Double (- B A)) convergence-tolerance)
        (Math/exp (/ A 2.0))
        (let [C (+ A (/ (* fA (- A B)) (- fB fA)))
              fC (f C)]
          (recur ; A<-(B|A), B<-C fA<-(fB|fA/2) fB<-fC
           (if (neg? (* fB fC))
             B
             A)
           C
           (if (neg? (* fB fC))
             fB
             (/ fA 2.0))
           fC))))))
