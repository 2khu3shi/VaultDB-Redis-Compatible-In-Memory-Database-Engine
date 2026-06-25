package com.vaultdb.data;

public sealed interface StoreValue permits StringValue, ListValue, HashValue, SetValue {
    ValueType type();
}
