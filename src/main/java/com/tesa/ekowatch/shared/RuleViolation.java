package com.tesa.ekowatch.shared;

public class RuleViolation extends RuntimeException {
    public RuleViolation(String code) {
        super(code);
    }

    public String code() {
        return getMessage();
    }
}
