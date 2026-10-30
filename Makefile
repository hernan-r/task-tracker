.PHONY: build test clean run

build:
	./build.sh

test:
	./test.sh

clean:
	rm -rf out task-tracker.jar

run: build
	java -jar task-tracker.jar help
