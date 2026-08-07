CREATE TABLE IF NOT EXISTS expense (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    description VARCHAR(255)   NOT NULL,
    amount      DECIMAL(12, 2) NOT NULL,
    category    VARCHAR(30)    NOT NULL,
    spent_on    DATE           NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_expense_spent_on ON expense(spent_on);
CREATE INDEX IF NOT EXISTS idx_expense_category ON expense(category);
