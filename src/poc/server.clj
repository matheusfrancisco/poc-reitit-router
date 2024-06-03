(ns poc.server
  (:require [io.pedestal.http :as server]
            [reitit.ring :as ring]
            [reitit.http :as http]
            [reitit.coercion.spec]
            [reitit.swagger :as swagger]
            [reitit.swagger-ui :as swagger-ui]
            [reitit.http.coercion :as coercion]
            [reitit.dev.pretty :as pretty]
            [reitit.http.interceptors.parameters :as parameters]
            [reitit.http.interceptors.muuntaja :as muuntaja]
            [reitit.http.interceptors.exception :as exception]
            [reitit.http.interceptors.multipart :as multipart]
            ;; Uncomment to use
            ; [reitit.http.interceptors.dev :as dev]
            ; [reitit.http.spec :as spec]
            ; [spec-tools.spell :as spell]
            [reitit.pedestal :as pedestal]
            [clojure.core.async :as a]
            [clojure.java.io :as io]
            [muuntaja.core :as m]))

(defn interceptor [number]
  {:enter (fn [ctx] (a/go (update-in ctx [:request :number] (fnil + 0) number)))})

(def router
  (pedestal/routing-interceptor
   (http/router
    [["/swagger.json"
      {:get {:no-doc true
             :swagger {:info {:title "my-api"
                              :description "with pedestal & reitit-http"}}
             :handler (swagger/create-swagger-handler)}}]

     ["/math"
      {:swagger {:tags ["math"]}}

      ["/plus"
       {:get {:summary "plus with spec query parameters"
              :parameters {:query {:x int?, :y int?}}
              :handler (fn [{{:keys [x y z]} :query-params :as r}]
                         (prn r)
                         (prn z)
                         (prn (Integer/parseInt x))
                         (prn y)
                         {:status 200
                          :body {:total (Integer/parseInt x)}})}
        :post {:summary "plus with spec body parameters"
               :parameters {:body {:x int?, :y int?}}
               :responses {200 {:body {:total int?}}}
               :handler (fn [{{{:keys [x y]} :body} :parameters}]
                          {:status 200
                           :body {:total (+ x y)}})}}]]]

    {:exception pretty/exception
     :data {:coercion reitit.coercion.spec/coercion
            :muuntaja m/instance
            :interceptors [;; swagger feature
                           swagger/swagger-feature
                             ;; query-params & form-params
                           (parameters/parameters-interceptor)
                             ;; content-negotiation
                           (muuntaja/format-negotiate-interceptor)
                             ;; encoding response body
                           (muuntaja/format-response-interceptor)
                             ;; exception handling
                           (exception/exception-interceptor)
                             ;; decoding request body
                           ;;;(muuntaja/format-request-interceptor)
                             ;; coercing response bodys
                           (coercion/coerce-response-interceptor)
                             ;; coercing request parameters
                           ;;(coercion/coerce-request-interceptor)
                             ;; multipart
                           (multipart/multipart-interceptor)]}})

    ;; optional default ring handler (if no routes have matched)
   (ring/routes
    (swagger-ui/create-swagger-ui-handler
     {:path "/"
      :config {:validatorUrl nil
               :operationsSorter "alpha"}})
    (ring/create-resource-handler)
    (ring/create-default-handler))))

(defn start []
  (-> {:env :dev
       ::server/type :jetty
       ::server/port 3000
       ::server/join? false
       ;; no pedestal routes
       ::server/routes []
       ;; allow serving the swagger-ui styles & scripts from self
       ::server/secure-headers {:content-security-policy-settings
                                {:default-src "'self'"
                                 :style-src "'self' 'unsafe-inline'"
                                 :script-src "'self' 'unsafe-inline'"}}}
      (server/default-interceptors)
      ;; use the reitit router
      (pedestal/replace-last-interceptor router)
      (server/dev-interceptors)
      (server/create-server)
      (server/start)))

(comment
  (def s (start))

  ;
  (server/stop s)

;
  )
