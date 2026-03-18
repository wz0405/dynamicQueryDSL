DELETE FROM ORDERS WHERE 1=1;
DELETE FROM PAYMENTS WHERE 1=1;
DELETE FROM ORDER_HISTORY WHERE 1=1;

-- 주문 마스터
CREATE TABLE IF NOT EXISTS ORDERS (
                                      ORDER_ID        VARCHAR(30)  PRIMARY KEY,
    STORE_ID        VARCHAR(10)  NOT NULL,
    MERCHANT_ID     VARCHAR(20)  NOT NULL,
    ORDER_DT        CHAR(8)      NOT NULL,
    STATUS          CHAR(1)      NOT NULL DEFAULT '0',  -- 0:정상 1:취소 2:부분취소
    AMOUNT          BIGINT       NOT NULL DEFAULT 0,
    BUYER_NAME      VARCHAR(50),
    BUYER_NAME_ENC  VARCHAR(100)
    );

-- 결제 상세
CREATE TABLE IF NOT EXISTS PAYMENTS (
                                        ORDER_ID        VARCHAR(30)  NOT NULL,
    ORDER_DT        CHAR(8)      NOT NULL,
    APPROVAL_NO     VARCHAR(20),
    CARD_NO_ENC     VARCHAR(100),
    PRIMARY KEY (ORDER_ID, ORDER_DT)
    );

-- 주문 이력
CREATE TABLE IF NOT EXISTS ORDER_HISTORY (
                                             ORDER_ID        VARCHAR(30)  NOT NULL,
    SETTLE_DT       CHAR(8),
    STATUS          CHAR(1),
    AMOUNT          BIGINT,
    PRIMARY KEY (ORDER_ID)
    );

-- 샘플 데이터
INSERT INTO ORDERS VALUES
                       ('ORD001', 'store01', 'merchant01', '20260110', '0', 300000,  'Alice',   'enc_alice'),
                       ('ORD002', 'store01', 'merchant01', '20260115', '0', 150000,  'Bob',     'enc_bob'),
                       ('ORD003', 'store01', 'merchant01', '20260120', '2', 500000,  'Charlie', 'enc_charlie'),
                       ('ORD004', 'store01', 'merchant01', '20260125', '0', 1200000, 'David',   'enc_david'),
                       ('ORD005', 'store01', 'merchant02', '20260128', '0', 80000,   'Alice',   'enc_alice');

INSERT INTO PAYMENTS VALUES
                         ('ORD001', '20260110', 'APV10001', 'card_enc_001'),
                         ('ORD002', '20260115', 'APV10002', 'card_enc_002'),
                         ('ORD003', '20260120', 'APV10001', 'card_enc_003'),
                         ('ORD004', '20260125', 'APV10003', 'card_enc_004');

INSERT INTO ORDER_HISTORY VALUES
                              ('ORD001', '20260111', '0',  300000),
                              ('ORD002', '20260116', '0',  150000),
                              ('ORD003', '20260126', '2', -500000),
                              ('ORD004', '20260126', '0', 1200000);