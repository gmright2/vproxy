package net.cassite.vproxy.util;

public enum LogType {
    UNEXPECTED,
    IMPROPER_USE,
    SERVER_ACCEPT_FAIL,
    CONN_ERROR,
    EVENT_LOOP_ADD_FAIL,
    NO_CLIENT_CONN,
    EVENT_LOOP_CLOSE_FAIL,
    HEALTH_CHECK_CHANGE,
    NO_EVENT_LOOP,
    WRR_RANGE_LIST_MISMATCH,
}
