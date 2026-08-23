package net.maddkraft.maddprestige.platform.paper.i18n;

import java.util.Objects;

/** Bounded result of an atomic server-global locale reload. */
public record LocaleReloadResult(boolean successful, String locale, String code, String detail) {
    public LocaleReloadResult {
        locale = Objects.requireNonNull(locale, "locale");
        code = Objects.requireNonNull(code, "code");
        detail = Objects.requireNonNull(detail, "detail");
    }
}
