## Purpose

Defines the browser Monitor UI information architecture and interaction contracts for the redesigned multi-tab ops console, including the workbench home, merged SQL sub-modes, and batch step gating—without changing backend API requirements.

## ADDED Requirements

### Requirement: Workbench is the default landing view
The Monitor UI SHALL present a Workbench tab as the default active view after successful authentication (or when authentication is disabled). The Workbench MUST show an environment summary derived from existing monitor APIs and MUST provide shortcut actions that navigate to SQL dual-view, batch jobs, and configuration within the same page (no new browser route required).

#### Scenario: Default tab after login
- **WHEN** an operator opens `/monitor/` and is allowed to use the main UI
- **THEN** the Workbench view is active by default
- **AND** the previous default of landing on the single-value crypto tab MUST NOT apply

#### Scenario: Workbench shortcuts navigate in-page
- **WHEN** the operator activates a Workbench shortcut for SQL dual-view, batch, or configuration
- **THEN** the corresponding main tab becomes active
- **AND** the browser remains on the monitor path (no navigation to playground)

### Requirement: Main navigation uses top tabs including merged SQL
The Monitor UI SHALL expose top-level tabs for at least: Workbench, single-value crypto, single-row verify, batch jobs, SQL, data initialization, and configuration. The former separate tabs for SQL parse, SQL parameter encrypt, and SQL query MUST be merged into one SQL tab with mutually exclusive sub-modes. The SQL tab's default sub-mode MUST be dual-view query.

#### Scenario: SQL sub-modes available under one tab
- **WHEN** the operator opens the SQL tab
- **THEN** they can switch among dual-view query, SQL parse, and parameter encrypt without leaving the SQL tab
- **AND** the default sub-mode is dual-view query

#### Scenario: SQL draft preserved across sub-mode switches
- **WHEN** the operator enters SQL text in one SQL sub-mode and switches to another SQL sub-mode in the same session
- **THEN** the entered SQL text remains available (not cleared solely by sub-mode switch)

### Requirement: Batch apply is gated on successful preview
The batch jobs UI MUST present a visible step progression for selecting scope, previewing changes, and confirming apply. The apply control MUST remain disabled until a successful preview has completed for the current job; after a successful preview the operator MUST be able to confirm apply using the existing batch apply API.

#### Scenario: Apply disabled before preview
- **WHEN** the operator is on the batch tab and has not completed a successful preview for the current inputs
- **THEN** the apply/confirm control is disabled or otherwise non-operative

#### Scenario: Apply enabled after successful preview
- **WHEN** a batch preview succeeds and returns a usable job identifier
- **THEN** the apply/confirm control becomes available
- **AND** invoking it calls the existing batch apply endpoint with that job

### Requirement: Existing monitor tool capabilities remain reachable
The redesigned UI MUST continue to expose single-value encrypt/decrypt/sign/verify, single-row verify, configuration overview, and data initialization using the existing monitor APIs. Visual restyling MUST NOT remove these capabilities from the UI.

#### Scenario: Crypto and verify still callable from UI
- **WHEN** the operator uses the crypto or single-row verify tabs after the redesign
- **THEN** the UI still invokes the corresponding existing authenticated monitor APIs and displays success or failure results

#### Scenario: Static asset paths remain under monitor path
- **WHEN** the browser loads the monitor entry page stylesheet and script
- **THEN** they are served under the configured monitor path (default `/monitor/css/...` and `/monitor/js/...`)

### Requirement: No new backend API required for workbench summary
The Workbench environment summary MUST be populated by composing existing monitor APIs (at minimum configuration and datasources). The system MUST NOT require a new overview endpoint to satisfy the Workbench requirement; an optional overview endpoint MAY be added later but is not required by this capability.

#### Scenario: Workbench loads without overview endpoint
- **WHEN** only existing `config` and `datasources` APIs are available
- **THEN** the Workbench can still render mode/flags and datasource-related summary information without calling `/api/overview.json`
