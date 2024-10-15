terraform {
  required_providers {
    rabbitmq = {
      source = "cyrilgdn/rabbitmq"
      version = "1.8.0"
    }
  }
}

provider "rabbitmq" {
  endpoint = "http://localhost:15672"
  username = "admin"
  password = "admin"
}

resource "rabbitmq_queue" "advisor" {
  name  = "advisor_queue"
  vhost = "/"

  settings {
    durable     = true
    auto_delete = false
    arguments = {
      "x-queue-type" : "classic",
    }
  }
}

resource "rabbitmq_queue" "analyzer" {
  name  = "analyzer_queue"
  vhost = "/"

  settings {
    durable     = true
    auto_delete = false
    arguments = {
      "x-queue-type" : "classic",
    }
  }
}

resource "rabbitmq_queue" "config" {
  name  = "config_queue"
  vhost = "/"

  settings {
    durable     = true
    auto_delete = false
    arguments = {
      "x-queue-type" : "classic",
    }
  }
}

resource "rabbitmq_queue" "evaluator" {
  name  = "evaluator_queue"
  vhost = "/"

  settings {
    durable     = true
    auto_delete = false
    arguments = {
      "x-queue-type" : "classic",
    }
  }
}

resource "rabbitmq_queue" "orchestrator" {
  name  = "orchestrator_queue"
  vhost = "/"

  settings {
    durable     = true
    auto_delete = false
    arguments = {
      "x-queue-type" : "classic",
    }
  }
}

resource "rabbitmq_queue" "reporter" {
  name  = "reporter_queue"
  vhost = "/"

  settings {
    durable     = true
    auto_delete = false
    arguments = {
      "x-queue-type" : "classic",
    }
  }
}

resource "rabbitmq_queue" "scanner" {
  name  = "scanner_queue"
  vhost = "/"

  settings {
    durable     = true
    auto_delete = false
    arguments = {
      "x-queue-type" : "classic",
    }
  }
}
