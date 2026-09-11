(ns bricklaying.advisor
  "Bricklaying Advisor — proposing a masonry job-site scheduling/logistics
  coordination operation (log a work record, schedule a crew
  operation, flag a safety concern, coordinate a masonry-materials
  supply order) from a crew roster, job-site registration and
  safety-reporting policy. Swappable mock/llm; the advisor ONLY
  proposes — `bricklaying.governor` independently gates every
  proposal and always escalates safety concerns and above-threshold
  supply orders. The advisor never proposes to directly finalize a
  masonry-work-execution decision (e.g. a specific wall-laying or
  mortar-joint step) or to override a site safety officer's/foreman's
  judgment — those stay permanently out of this actor's scope.
  Modeled on cloud-itonami-isco-3313's advisor (and closely on
  cloud-itonami-isco-7111's housebuilder.advisor for the
  job-site-coordination domain shape).

  A proposal: {:op :log-work-record|:schedule-crew-operation|
               :flag-safety-concern|:coordinate-supply-order
               :effect :propose :worker-id str :site-id str
               :cost number :hazard-type kw :task str :stake kw
               :confidence n :rationale str}")

(defprotocol Advisor
  (-advise [advisor store request] "request -> proposal map"))

(defn- rationale-for [op worker-id site-id hazard-type]
  (case op
    :log-work-record
    (str "logged work record for worker " worker-id " at site " site-id)

    :schedule-crew-operation
    (str "scheduled crew operation for masonry task at site " site-id)

    :flag-safety-concern
    (str "flagged " (name (or hazard-type :hazard)) " concern for worker "
         worker-id " at site " site-id " — routed for site safety officer review")

    :coordinate-supply-order
    (str "coordinated supply order for worker " worker-id " at site " site-id)

    (str "proposed " (name op) " for worker " worker-id " at site " site-id)))

(defn- infer [_store {:keys [op stake worker-id site-id cost hazard-type task]
                       :as request}]
  {:op op
   :effect :propose
   :worker-id worker-id
   :site-id site-id
   :cost cost
   :hazard-type hazard-type
   :task task
   :stake (or stake :low)
   :confidence (case (or stake :low) :high 0.7 :medium 0.85 :low 0.95)
   :rationale (rationale-for op worker-id site-id hazard-type)})

(defn mock-advisor []
  (reify Advisor
    (-advise [_ store request] (infer store request))))

(def ^:private system-prompt
  "You are a masonry job-site scheduling/logistics coordination
   advisor. Given a request, propose an :op (one of :log-work-record,
   :schedule-crew-operation, :flag-safety-concern,
   :coordinate-supply-order), the :worker-id, :site-id, and any
   :cost/:hazard-type/:task fields, an honest :confidence and a
   :stake. Never propose an op outside this closed list, and never
   propose to directly finalize a masonry-work-execution decision
   (e.g. deciding to proceed with a specific wall-laying or
   mortar-joint step), or to override a site safety officer's or
   foreman's judgment — those are always out of this actor's scope; it
   coordinates job-site scheduling/logistics only and never performs
   masonry work or authorizes masonry-work execution itself. Safety
   concerns always require human sign-off regardless of confidence.")

(defn- parse-proposal [content]
  (try
    (let [p (read-string content)]
      (if (map? p)
        (assoc p :effect :propose)
        {:op :unknown :effect :propose :confidence 0.0 :stake :high
         :rationale "unparseable LLM response"}))
    (catch #?(:clj Exception :cljs js/Error) _
      {:op :unknown :effect :propose :confidence 0.0 :stake :high
       :rationale "LLM response parse failure"})))

(defn llm-advisor
  [chat-model model-generate-fn gen-opts]
  (reify Advisor
    (-advise [_ _store request]
      (let [msgs [{:role :system :content system-prompt}
                  {:role :user :content (str "operation request: " (pr-str request))}]
            resp (model-generate-fn chat-model msgs gen-opts)]
        (parse-proposal (:content resp))))))
