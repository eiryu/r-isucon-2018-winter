PORT ?= 3000

setup:
	bundle install --path vendor/bundle

run:
	bundle exec rackup -p $(PORT) -o 0.0.0.0

.PHONY: setup run
