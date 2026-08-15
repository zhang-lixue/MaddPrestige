package net.maddkraft.maddprestige.core.rank;

import net.maddkraft.maddprestige.api.result.Result;

public interface ExternalGroupCatalog {
    Result<Boolean> exists(String groupName);
}
