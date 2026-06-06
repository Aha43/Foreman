APP_NAME   := Foreman
MAIN_CLASS := foreman.app.ForemanMain
MAIN_JAR   := $(APP_NAME).jar
WORKSPACE      := $(HOME)/.foreman/workspace.json
WORKSPACE_DEV  := $(HOME)/.foreman/dev/workspace.json

SRC_DIR      := src
LIB_DIR      := lib
TEST_LIB_DIR := lib/test
BUILD_DIR    := build
CLASSES_DIR  := $(BUILD_DIR)/classes
TEST_CLASSES := $(BUILD_DIR)/test-classes
APP_DIR      := $(BUILD_DIR)/app

SOURCES      := $(shell find $(SRC_DIR) -name "*.java")
TEST_SOURCES := $(shell find test -name "*.java" 2>/dev/null)

JUNIT_JAR := $(TEST_LIB_DIR)/junit-platform-console-standalone-1.10.2.jar

.PHONY: clean classes jar app run all test help

all: app

help:
	@echo "Usage: make <target>"
	@echo ""
	@echo "  all         Build the app (default)"
	@echo "  run         Build and launch the app (prod mode)"
	@echo "  run-dev     Build and launch the app (dev mode — separate workspace)"
	@echo "  test        Compile and run unit tests"
	@echo "  clean       Delete build output"
	@echo ""

clean:
	rm -rf $(BUILD_DIR)

classes:
	rm -rf $(CLASSES_DIR)
	mkdir -p $(CLASSES_DIR)
	javac \
		-cp "$(LIB_DIR)/*" \
		-d $(CLASSES_DIR) \
		$(SOURCES)
	@if [ -d $(SRC_DIR)/icons ];     then cp -r $(SRC_DIR)/icons     $(CLASSES_DIR)/; fi
	@if [ -d $(SRC_DIR)/resources ]; then cp -r $(SRC_DIR)/resources $(CLASSES_DIR)/; fi

jar: classes
	mkdir -p $(APP_DIR)
	jar \
		--create \
		--file $(APP_DIR)/$(MAIN_JAR) \
		--main-class $(MAIN_CLASS) \
		-C $(CLASSES_DIR) . \
		-C . VERSION

app: jar
	rm -rf $(APP_DIR)/lib
	mkdir -p $(APP_DIR)/lib
	cp $(LIB_DIR)/*.jar $(APP_DIR)/lib/

run: app
	java \
		-cp "$(APP_DIR)/$(MAIN_JAR):$(APP_DIR)/lib/*" \
		$(MAIN_CLASS)

run-dev: app
	java \
		-cp "$(APP_DIR)/$(MAIN_JAR):$(APP_DIR)/lib/*" \
		$(MAIN_CLASS) --dev

test: classes
	rm -rf $(TEST_CLASSES)
	mkdir -p $(TEST_CLASSES)
	javac \
		-cp "$(LIB_DIR)/*:$(JUNIT_JAR):$(CLASSES_DIR)" \
		-d $(TEST_CLASSES) \
		$(TEST_SOURCES)
	java -cp "$(CLASSES_DIR):$(TEST_CLASSES):$(LIB_DIR)/*:$(JUNIT_JAR)" \
		org.junit.platform.console.ConsoleLauncher execute \
		--scan-class-path "$(TEST_CLASSES)"
