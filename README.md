# Glickit

A Glicko-2 implementation written in Clojure.

Original system by [Mark Glickman](http://www.glicko.net/).

## Background

Glicko-2 is a rating system created by [Mark Glickman](http://www.glicko.net/). The goal of the system is to compare skill levels in two-player games such as chess. Some information:

* Glicko-2 is in the public domain
* Glicko-2 (and Glicko-1) are only implemented for two-player games (chess, etc). For many-player games, different systems exist: Microsoft's [TrueSkill](https://www.microsoft.com/en-us/research/project/trueskill-ranking-system/) system uses a proprietary black-box model; [Weng and Lin](http://www.jmlr.org/papers/v12/weng11a.html) examine Bayesian strategies; [Menke and Martinez](https://link.springer.com/article/10.1007/s00521-006-0080-8) examine the use of a neural network
* Glicko-2 is not a drop-in replacement for the Glicko-1 or ELO systems

With that in mind, let's get started!

## Usage

Some familiarity with ranking systems (what they are, what problems they solve, a general sense of how they work) is assumed.

Each player has three values: rating, deviation, and volatility. Rating is the relative strength of the player, and is used to compare two players' skills. Rating plus/minus twice the deviation provides a 95% confidence interval. Volatility accounts for differences in performance, and its impact can be adjusted with the variable tau, which will be discussed later.

Glicko-1 and Glicko-2 use different scalings for rating and deviation (volatility remains the same). A general rule of thumb is that Glicko-1 has larger rating and deviation values than Glicko-2. I will try to include both through this documentation.

Converting from Glicko-1 to Glicko-2 is simple:

* Rating-2 = (Rating-1 - 1500) / 173.7178
* Deviation-2 = Deviation-1 / 173.7178

Converting back:

* Rating-1 = (Rating-2 * 173.7178) + 1500
* Deviation-1 = Deviation-2 * 173.7178

These functions have been provided `[glickit.helpers]`.

Glickman recommends the following values for new players (Glicko-1, Glicko-2):

* Rating: 1500 (0)
* Deviation: 350 (2.014761872416068)
* Volatility: 0.06 (0.06)

Volatility may be changed to a different default value if desired.

Games are grouped into rating periods. Rating, deviation, and volatility are assumed to remain consistent and are updated at the end of each period. Glickman suggests that a rating period should contain ~10-12 games.

This implementation requires the following values to update the rank of a single player:

* Tau
* Player rating
* Player deviation
* Player volatility
* A sequence of opponent ratings
* A sequence of opponent deviations
* A sequence of game outcomes

Two things to note:
* The sequence can be a list or a vector. In theory it can be any iterable sequence, but this has not been tested with any other type.
* Game outcomes are relative to the player being evaluated. Wins are represented as 1, ties 0.5, and losses 0.

There is also an inactive-update function. This implements "rating decay" by increasing the deviation for a player every missed rating period. This is a multiple-arity function, and can take:

* Rating, deviation, volatility
* Periods inactive, rating, deviation, volatility

I have tried to make this implementation as organization-agnostic as possible, and have avoided destructuring at the function level.

While this gives a large degree of freedom, defining a "main" rating function is much more ergonomic. An example:

```clojure

(require '[glickit.core :refer [glicko-rank glicko2->glicko1 rating1->rating2 dev1->dev2 inactive-update]])

; Our test player, set up as a map. No games in a rating period are represented as empty vectors.
(def test-player
  {:player-rating 1500.0
   :player-dev 350
   :player-vol 0.06
   :opponent-ratings []
   :opponent-devs []
   :outcomes []})

; A rather large value for tau.
(def tau 1.0)

(defn rate
  [{:keys [player-rating
           player-dev
           player-vol
           opponent-ratings
           opponent-devs
           outcomes]}]
  ; Will convert to Glicko-2 values and back again.
  (let [converted-rating (rating1->rating2 player-rating)
        converted-dev (dev1->dev2 player-dev)
        converted-opponent-ratings (map #(rating1->rating2 %) opponent-ratings)
        converted-opponent-devs (map #(dev1->dev2 %) opponent-devs)]
      ; This function is meant to be run every rating period.
      ; If the player has been inactive, runs inactive-update.
      (if (and (empty? opponent-ratings)
               (empty? opponent-devs)
               (empty? outcomes))
        (let [[new-score new-dev new-vol] (apply glicko2->glicko1 (inactive-update converted-rating converted-dev player-vol))]
          {:player-rating new-score
            :player-dev new-dev
            :player-vol new-vol
            :opponent-ratings []
            :opponent-devs []
            :outcomes []})
        (let [[new-score new-dev new-vol] (apply glicko2->glicko1 ((partial glicko-rank tau) converted-rating converted-dev player-vol converted-opponent-ratings converted-opponent-devs outcomes))]
          {:player-rating new-score
            :player-dev new-dev
            :player-vol new-vol
            :opponent-ratings []
            :opponent-devs []
            :outcomes []}))))

(rate test-player)
```

Note that all functions, if they return multiple values, return a vector of the form [player-rating player-dev player-vol]. This is to allow for easier destructuring and conversion; the shift back to map should happen at the end if needed.

## Example

We will walk through the example on [Mark Glickman's website](http://www.glicko.net/glicko/glicko2.pdf).

The namespace that implements the main Glicko functionality is ```glickit.core```. This contains the function ```glicko-rank``` as well as several helper functions that convert between the Glicko scales. For the internals of the Glicko system (g, v, delta, etc) see ```[glickit.ranker]```.

```clojure
(require '[glickit.core :refer [glicko-rank glicko2->glicko1 rating1->rating2 dev1->dev2 inactive-update]])

(def test-player
  {:player-rating 1500.0
   :player-dev 200.0
   :player-vol 0.06
   :opponent-ratings [1400.0 1550.0 1700.0]
   :opponent-devs [30.0 100.0 300.0]
   :outcomes [1.0 0.0 0.0]})

(def tau 0.5)

; An example of the rating function "a la carte." You can pick and choose if you want to input Glicko-2 values, if you want to change tau based on the player, etc.
; This is a good bare minimum that takes in a global tau and Glicko-1 values.
(defn rate
  [{:keys [player-rating
           player-dev
           player-vol
           opponent-ratings
           opponent-devs
           outcomes]}]
  ; Will convert to Glicko-2 values and back again.
  (let [converted-rating (rating1->rating2 player-rating)
        converted-dev (dev1->dev2 player-dev)
        converted-opponent-ratings (map #(rating1->rating2 %) opponent-ratings)
        converted-opponent-devs (map #(dev1->dev2 %) opponent-devs)]
      ; This function is meant to be run every rating period.
      ; If the player has been inactive, runs inactive-update.
      (if (and (empty? opponent-ratings)
               (empty? opponent-devs)
               (empty? outcomes))
        (let [results (apply glicko2->glicko1 (inactive-update converted-rating converted-dev player-vol))]
          {:player-rating (results 0)
            :player-dev (results 1)
            :player-vol (results 2)
            :opponent-ratings []
            :opponent-devs []
            :outcomes []})
        (let [results (apply glicko2->glicko1 ((partial glicko-rank tau) converted-rating converted-dev player-vol converted-opponent-ratings converted-opponent-devs outcomes))]
          {:player-rating (results 0)
            :player-dev (results 1)
            :player-vol (results 2)
            :opponent-ratings []
            :opponent-devs []
            :outcomes []}))))

(rate test-player)
```

Note that the value for the rating is 0.01 off of the canonical Glickman answer. This is because he implements some strange rounding in the g-function calculation earlier in the paper that I have not been able to replicate. I do not believe this is symptomatic of a larger problem; however, user discretion is advised.

## Compatibility

This implementation is written in Clojure 1.9.0 for the JVM. I believe it should be compatible with any Clojure version that includes reduce (or made compatible with few changes). Support for ClojureScript is planned; the only thing preventing compatibility in this case is the math (which uses the native Java math library).

## Other Implementations

[This](https://github.com/ageneau/glicko2) is another implementation of the Glicko-2 system. If this library doesn't work for whatever reason, this may serve as a reasonable alternative. No insult is intended!

## Contact

Please feel free to contact me with any bugs or complaints at schmic41@gmail.com.

## License

MIT License

Copyright (c) 2018

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
