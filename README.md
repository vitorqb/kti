# KTI - Keep This Info

**work in progress**

_generated using Luminus version "3.10.40"_

KTI is a small app to learn web programming in clojure.

It is a backend to make it easy to capture things you want to check later.

Nothing is better than an example:

1) Capture something interesting you want to check later
```
POST /captured-references
{
  "reference": "https://clojuredocs.org/"
}
-----
201 Created
{
  "id": 2,
  "reference": "https://clojuredocs.org/",
  "created-at": "2019-03-02T10:23:33",
  "classified": false
}
```

2) When you have time, check your captured references
```
GET /captured-references
-----
200 OK
[
  {
    "id": 1,
    "reference": "https://www.youtube.com/watch?v=aG2uddkKWYE",
    "created-at": "2019-03-02T10:13:30",
    "classified": false
  },
  {
    "id": 2,
    "reference": "https://clojuredocs.org/",
    "created-at": "2019-03-02T10:23:33",
    "classified": false
  }
]
```

3) Quickly check the reference and classify it properly, creating an article
```
POST /articles
{
  "id-captured-reference": 2,
  "description": "Get familiar with amazing clojure docs!",
  "action-link": "https://clojuredocs.org/",
  "tags": ["clojure", "website"]
}
-----
201 Created
{
  "id": 3,
  "id-captured-reference": 2,
  "description": "Get familiar with amazing clojure docs!",
  "action-link": "https://clojuredocs.org/",
  "tags": [
    "clojure",
    "website"
  ]
}
```

4) Read it, learn, and make a review
```
POST /reviews
{
    "id-article": 3,
    "feedback-text": "Clojure docs is amazing, and the examples rock!",
    "state": "COMPLETED"
}
------
201 Created
{
    "id": 1,
    "id-article": 3,
    "feedback-text": "Clojure docs is amazing, and the examples rock!",
    "state": "COMPLETED"
}
```


## Prerequisites

You will need [Leiningen][1] 2.0 or above installed.

[1]: https://github.com/technomancy/leiningen

## Running

To start a web server for the application, run:

    lein run 

## License

Copyright © 2019 Vitor Quintanilha Barbosa (vitorqb@gmail.com)
