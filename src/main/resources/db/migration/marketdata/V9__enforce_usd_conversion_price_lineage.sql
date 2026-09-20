ALTER TABLE marketdata.usd_conversion_facts
    ADD CONSTRAINT usd_conversion_facts_converted_price_fk
        FOREIGN KEY (chain_id, transaction_value, event_locator)
        REFERENCES marketdata.price_observations (chain_id, transaction_value, event_locator),
    ADD CONSTRAINT usd_conversion_facts_quote_price_fk
        FOREIGN KEY (chain_id, usd_quote_transaction_value, usd_quote_event_locator)
        REFERENCES marketdata.price_observations (chain_id, transaction_value, event_locator);
