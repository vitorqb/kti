(ns kti.validation)

(defn validate
  "Runs all funs as validation functions.
  Returns nil on validation success and {:error-message ...} on error.
  Each fun must return nil on validation success or a string on error."
  {:style/indent 1}
  [arg & funs]
  (loop [todo funs]
    (if (nil? todo)
      nil
      (let [[f-head & f-tail] todo
            result (f-head arg)]
        (if (nil? result)
          (recur f-tail)
          {:error-msg result})))))
