# @file Makefile
# @author Stefan Wilhelm (wile)
# @license MIT
#
# GNU Make makefile based build relay.
# Note for reviewers/clones: This file is a auxiliary script for my setup.
# It's not needed to build the mod.
#
.PHONY: default init clean clean-all mrproper sanatize dist dist-all update-json sync-main-repo compare migrate-from-112

default:	;	@echo "First change to specific version directory."
dist: default

clean:
	-@cd 1.12; make -s clean
	-@cd 1.14; make -s clean
	-@cd 1.15; make -s clean

clean-all:
	-@cd 1.12; make -s clean-all
	-@cd 1.14; make -s clean-all
	-@cd 1.15; make -s clean-all

mrproper:
	-@cd 1.12; make -s mrproper
	-@cd 1.14; make -s mrproper
	-@cd 1.15; make -s mrproper

update-json:
	@echo "[main] Update update.json ..."
	@djs tasks.js update-json

sanatize:
	@cd 1.12; make -s sanatize
	@cd 1.14; make -s sanatize
	@cd 1.15; make -s sanatize
	@make -s update-json

init:
	-@cd 1.12; make -s init
	-@cd 1.14; make -s init
	-@cd 1.15; make -s init

dist-all: clean-all init
	-@cd 1.12; make -s dist
	-@cd 1.14; make -s dist
	-@cd 1.15; make -s dist

compare:
	@djs tasks.js compare-blockstates -v
	@djs tasks.js compare-textures -v

# For reviewers: I am using a local repository for experimental changes,
# this target copies the local working tree to the location of the
# repository that you cloned.
sync-main-repo: sanatize update-json
	@echo "[main] Synchronising to github repository working tree ..."
	@djs tasks.js sync-main-repository
