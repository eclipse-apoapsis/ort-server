val ruleSet = ruleSet(ortResult, licenseInfoResolver, resolutionProvider) {
    packageRule("FORBIDDEN_LICENSE") {
        require {
            -isProject()
        }

        licenseRule("FORBIDDEN_LICENSE", LicenseView.ONLY_DETECTED) {
            if (license.simpleLicense() in listOf("LicenseRef-detected1", "LicenseRef-detected2")) {
                error("Found forbidden license '${license.simpleLicense()}'.", "")
            }
        }
    }
}

ruleViolations += ruleSet.violations
