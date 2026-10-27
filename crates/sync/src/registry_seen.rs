//! Pure helpers for `POST /registry/{org}/seen?device=` (read receipts).

/// Edge URL for a seen report (Bearer auth; same device query as push-target).
pub fn seen_url(edge_base: &str, org_id: &str, device_id: &str) -> String {
    format!(
        "{}/registry/{}/seen?device={}",
        edge_base.trim_end_matches('/'),
        percent_encode(org_id),
        percent_encode(device_id)
    )
}

/// JSON body: `chatId`, `messageId`, `messageAt` (epoch ms).
pub fn seen_body(chat_id: &str, message_id: &str, message_at_ms: i64) -> serde_json::Value {
    serde_json::json!({
        "chatId": chat_id,
        "messageId": message_id,
        "messageAt": message_at_ms,
    })
}

fn percent_encode(value: &str) -> String {
    let mut out = String::with_capacity(value.len());
    for byte in value.bytes() {
        if byte.is_ascii_alphanumeric() || matches!(byte, b'-' | b'_' | b'.' | b'~') {
            out.push(byte as char);
        } else {
            out.push_str(&format!("%{byte:02X}"));
        }
    }
    out
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn seen_endpoint_shape() {
        assert_eq!(
            seen_url("https://e.sh/", "org_1", "ios-1"),
            "https://e.sh/registry/org_1/seen?device=ios-1"
        );
        let body = seen_body("c1", "m1", 1_700_000_000_000);
        assert_eq!(body["chatId"], "c1");
        assert_eq!(body["messageId"], "m1");
        assert_eq!(body["messageAt"], 1_700_000_000_000);
    }
}
