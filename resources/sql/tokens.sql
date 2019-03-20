-- :name get-current-token-for-email :* :1
SELECT value FROM tokens
JOIN users ON tokens.id_user = users.id
WHERE users.email = :email

-- :name delete-tokens-for-user :! :n
DELETE FROM tokens WHERE id_user = :id

-- :name create-token! :insert
INSERT INTO tokens (id_user, value) VALUES (:id-user, :value)

-- :name get-all-token-values :? :*
SELECT value FROM tokens

-- :name get-token-value :* :1
SELECT value FROM tokens WHERE id = :id
