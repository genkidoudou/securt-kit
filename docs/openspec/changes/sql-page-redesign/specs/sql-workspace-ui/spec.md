## Purpose

定义 Monitor SQL 工作区的查询与修改分区、模式状态隔离、受限结果浏览和响应式布局行为，使操作人员无需频繁滚动整页即可完成 SQL 查询、解析及参数加密。

## ADDED Requirements

### Requirement: SQL workspace separates query and modification workflows
The Monitor SQL workspace SHALL expose mutually exclusive primary views named 查询 and 修改. 查询 MUST be the default view. 修改 SHALL expose mutually exclusive secondary modes named SQL 解析 and 参数加密, with SQL 解析 selected by default when the page first loads.

#### Scenario: Open SQL workspace
- **WHEN** an operator opens the SQL tab
- **THEN** the 查询 primary view is active
- **AND** the modification controls are not displayed

#### Scenario: Open modification workflow
- **WHEN** an operator activates 修改
- **THEN** the modification workspace is displayed
- **AND** SQL 解析 is active unless the operator previously selected another modification mode in the same page session

#### Scenario: Switch modification modes
- **WHEN** an operator switches between SQL 解析 and 参数加密
- **THEN** only the selected modification mode is displayed
- **AND** the operator remains inside the 修改 primary view

### Requirement: SQL modes preserve independent working state
查询, SQL 解析, and 参数加密 SHALL each preserve their own SQL draft and displayed result during in-page mode switches. Switching modes MUST NOT copy, overwrite, or clear another mode's draft or result. A clear action MUST affect only the active mode.

#### Scenario: Switch away from a populated mode
- **WHEN** an operator enters SQL and obtains a result in one mode and then switches to another mode
- **THEN** the destination mode shows its own prior draft and result
- **AND** the source mode's draft and result remain available when the operator returns

#### Scenario: Clear active mode
- **WHEN** an operator activates the clear action in one SQL mode
- **THEN** only that mode's input and result are cleared
- **AND** drafts and results in the other SQL modes remain unchanged

### Requirement: Results remain within bounded work areas
Query rows, parse mappings, encrypt-field results, and encrypted SQL output SHALL be displayed in height-bounded result areas that scroll internally when their content exceeds the available space. Long result content MUST NOT continuously expand the document height.

#### Scenario: Query returns many rows
- **WHEN** a query result exceeds the visible height of its result area
- **THEN** the operator can scroll within the query result area
- **AND** the result does not continue increasing the SQL page's document height

#### Scenario: Parse or encryption output is long
- **WHEN** parse mappings or encrypted SQL exceed the visible result area
- **THEN** the corresponding result area provides internal scrolling
- **AND** input controls remain accessible without traversing the full result content

#### Scenario: Scroll query columns
- **WHEN** an operator scrolls vertically through a long query result
- **THEN** the query table header remains visible within the result area

### Requirement: Modification workspace adapts to viewport width
The modification workspace SHALL display input and output side by side when the viewport width is greater than 900 pixels. At 900 pixels or below, it MUST display the same panes vertically without horizontal page overflow.

#### Scenario: Desktop modification layout
- **WHEN** the viewport width is greater than 900 pixels
- **THEN** modification input and result panes are displayed as two columns

#### Scenario: Narrow modification layout
- **WHEN** the viewport width is 900 pixels or less
- **THEN** modification input and result panes are stacked vertically
- **AND** the SQL page does not introduce horizontal document scrolling

### Requirement: Existing SQL operations remain compatible
The redesigned workspace MUST retain the existing query, parse, and parameter-encryption operations, including their datasource selection, query pagination, result statistics, copy action, request fields, response handling, and success or failure feedback. The redesign MUST NOT require a new backend API.

#### Scenario: Execute existing SQL operations
- **WHEN** an operator executes query, parse, or parameter encryption from the redesigned workspace
- **THEN** the UI invokes the corresponding existing Monitor API with its existing request contract
- **AND** displays the returned success or failure result in the active mode

#### Scenario: Use existing element integrations
- **WHEN** existing JavaScript binds SQL controls and result elements by their established DOM identifiers
- **THEN** those identifiers remain available after the redesign
