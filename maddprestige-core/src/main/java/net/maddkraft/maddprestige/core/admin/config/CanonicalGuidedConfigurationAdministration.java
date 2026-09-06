package net.maddkraft.maddprestige.core.admin.config;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import net.maddkraft.maddprestige.api.cost.CostDefinition;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.metric.MetricValue;
import net.maddkraft.maddprestige.api.metric.MetricValueType;
import net.maddkraft.maddprestige.api.operation.Actor;
import net.maddkraft.maddprestige.api.reward.RewardDefinition;
import net.maddkraft.maddprestige.api.validation.ValidationSeverity;
import net.maddkraft.maddprestige.api.value.ExactDecimal;
import net.maddkraft.maddprestige.core.admin.AdministrationException;
import net.maddkraft.maddprestige.core.admin.PermissionSubject;
import net.maddkraft.maddprestige.core.admin.PhaseSixPermissions;
import net.maddkraft.maddprestige.core.config.phase3.PhaseThreeConfiguration;
import net.maddkraft.maddprestige.core.config.phase4.ActivePhaseFourConfiguration;
import net.maddkraft.maddprestige.core.config.phase4.PhaseFourConfiguration;
import net.maddkraft.maddprestige.core.requirement.RequirementDefinition;
import net.maddkraft.maddprestige.core.requirement.RequirementGroup;
import net.maddkraft.maddprestige.core.requirement.RequirementLeaf;
import net.maddkraft.maddprestige.core.requirement.RequirementNode;
import net.maddkraft.maddprestige.core.requirement.ScalingProfile;
import net.maddkraft.maddprestige.core.requirement.ScalingStrategy;
import net.maddkraft.maddprestige.core.requirement.TargetTransformer;
import net.maddkraft.maddprestige.core.scaling.PrestigeScalingSegment;
import net.maddkraft.maddprestige.core.scaling.SegmentScalingMode;
import net.maddkraft.maddprestige.core.scaling.SegmentTransition;
import net.maddkraft.maddprestige.core.yaml.LosslessYamlDocument;
import net.maddkraft.maddprestige.core.yaml.YamlPath;

/** Canonical, revision-bound implementation of the first guided Staff configuration editor. */
public final class CanonicalGuidedConfigurationAdministration implements GuidedConfigurationAdministration {
    private static final String VAULT_BALANCE_PROVIDER = "vault_balance";
    private static final String VAULT_ECONOMY_COST_PROVIDER = "vault_economy_cost";
    private static final String VAULT_ECONOMY_COST_TYPE = "vault_economy";
    private static final String VAULT_ECONOMY_REWARD_PROVIDER = "vault_economy_reward";
    private static final String VAULT_ECONOMY_REWARD_TYPE = "vault_economy";
    private static final String MCMO_PROVIDER = "mcmmo";
    private static final String TOTAL_LEVEL_METRIC = "total_level";
    private static final long MAX_SELECTABLE_LEVEL = 10_000;
    private static final long MAX_SELECTABLE_MONEY = 10_000;
    private static final Pattern PLAIN_DECIMAL = Pattern.compile("[0-9]+(?:\\.[0-9]+)?");
    private static final Pattern PLAIN_COUNT = Pattern.compile("[0-9]+");
    private static final Duration REVIEW_LIFETIME = Duration.ofMinutes(5);
    private final ConfigurationAdministrationService administration;
    private final Supplier<Optional<ActivePhaseFourConfiguration>> active;
    private final Clock clock;
    private final Map<UUID, MoneyReviewAuthority> moneyReviews = new ConcurrentHashMap<>();
    private final Map<UUID, RewardReviewAuthority> rewardReviews = new ConcurrentHashMap<>();
    private final Map<UUID, TotalSkillLevelReviewAuthority> totalSkillLevelReviews = new ConcurrentHashMap<>();
    private final Map<UUID, ScalingReviewAuthority> scalingReviews = new ConcurrentHashMap<>();
    private final Map<UUID, ScalingOverrideReviewAuthority> scalingOverrideReviews = new ConcurrentHashMap<>();

    public CanonicalGuidedConfigurationAdministration(
            ConfigurationAdministrationService administration,
            Supplier<Optional<ActivePhaseFourConfiguration>> active,
            Clock clock) {
        this.administration = Objects.requireNonNull(administration, "configuration administration");
        this.active = Objects.requireNonNull(active, "active configuration");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public PrestigeLevelPage prestigeLevels(PermissionSubject subject, int pageIndex, int pageSize) {
        subject.require(PhaseSixPermissions.CONFIG_VIEW);
        if (pageIndex < 0 || pageSize < 1 || pageSize > 45) {
            throw new IllegalArgumentException("Level page and size are outside the visual editor bounds");
        }
        ActivePhaseFourConfiguration snapshot = active();
        long maximum = snapshot.phaseFour().configuration().prestige().limit().maximum()
                .orElse(MAX_SELECTABLE_LEVEL);
        maximum = Math.min(maximum, MAX_SELECTABLE_LEVEL);
        long first = Math.addExact(Math.multiplyExact((long) pageIndex, pageSize), 1);
        if (first > maximum) {
            throw new AdministrationException("config.gui.level_page.invalid",
                    "The selected Prestige level page is outside the configured range.",
                    "Return to Prestige Levels and select an available page.");
        }
        long last = Math.min(maximum, Math.addExact(first, pageSize - 1L));
        ArrayList<Long> levels = new ArrayList<>();
        for (long level = first; level <= last; level++) {
            levels.add(level);
        }
        return new PrestigeLevelPage(revision(snapshot), levels, pageIndex, pageIndex > 0, last < maximum);
    }

    @Override
    public PrestigeLevelConfigurationView prestigeLevel(PermissionSubject subject, long prestigeLevel) {
        subject.require(PhaseSixPermissions.CONFIG_VIEW);
        ActivePhaseFourConfiguration snapshot = active();
        requireLevel(snapshot, prestigeLevel);
        PhaseFourConfiguration phaseFour = snapshot.phaseFour().configuration();
        String moneyRequirement = "";
        String moneyCost = "";
        String scaling = "Complex configuration";
        boolean moneyAvailable = false;
        boolean moneyEditable = false;
        try {
            MoneyBinding binding = moneyBinding(snapshot, prestigeLevel);
            moneyRequirement = binding.requirementAmount();
            moneyCost = binding.costAmount();
            scaling = binding.scalingDescription();
            moneyAvailable = true;
            moneyEditable = binding.editable();
        } catch (AdministrationException exception) {
            if (!exception.code().equals("config.gui.money.unavailable")
                    && !exception.code().equals("config.gui.money.unsupported")) {
                throw exception;
            }
        }
        String rewardAmount = "";
        boolean rewardAvailable = false;
        boolean rewardEditable = false;
        try {
            RewardBinding binding = rewardBinding(snapshot, prestigeLevel);
            rewardAmount = binding.amount();
            rewardAvailable = true;
            rewardEditable = binding.editable();
        } catch (AdministrationException exception) {
            if (!exception.code().equals("config.gui.reward.unavailable")
                    && !exception.code().equals("config.gui.reward.unsupported")) {
                throw exception;
            }
        }
        return new PrestigeLevelConfigurationView(revision(snapshot), prestigeLevel,
                phaseFour.prestige().enabled(), moneyRequirement, moneyCost,
                phaseFour.prestige().rewardIds().size(), rewardAmount, scaling,
                moneyAvailable, moneyEditable, rewardAvailable, rewardEditable);
    }

    @Override
    public MoneyAmountPage moneyAmounts(
            PermissionSubject subject,
            long prestigeLevel,
            int pageIndex,
            int pageSize) {
        requireMutationPermissions(subject);
        PrestigeLevelConfigurationView level = prestigeLevel(subject, prestigeLevel);
        if (!level.moneyAvailable() || !level.moneyEditable()) {
            throw unsupportedMoneyEditor();
        }
        if (pageIndex < 0 || pageSize < 1 || pageSize > 45) {
            throw new IllegalArgumentException("Money page and size are outside the visual editor bounds");
        }
        long first = Math.addExact(Math.multiplyExact((long) pageIndex, pageSize), 1);
        if (first > MAX_SELECTABLE_MONEY) {
            throw new AdministrationException("config.gui.money_page.invalid",
                    "The selected Money amount page is outside the guided range.",
                    "Return to the Money editor and select an available page.");
        }
        long last = Math.min(MAX_SELECTABLE_MONEY, Math.addExact(first, pageSize - 1L));
        ArrayList<String> amounts = new ArrayList<>();
        for (long amount = first; amount <= last; amount++) {
            amounts.add(Long.toString(amount));
        }
        return new MoneyAmountPage(level.revision(), prestigeLevel, currentAmount(level), amounts, pageIndex,
                pageIndex > 0, last < MAX_SELECTABLE_MONEY);
    }

    @Override
    public GuidedNumericConfigurationInput moneyInput(PermissionSubject subject, long prestigeLevel) {
        requireMutationPermissions(subject);
        ActivePhaseFourConfiguration snapshot = active();
        requireLevel(snapshot, prestigeLevel);
        MoneyBinding binding = moneyBinding(snapshot, prestigeLevel);
        if (!binding.editable()) {
            throw unsupportedMoneyEditor();
        }
        return new GuidedNumericConfigurationInput(
                revision(snapshot), prestigeLevel, binding.costAmount());
    }

    @Override
    public String validateMoneyInput(
            PermissionSubject subject,
            long prestigeLevel,
            String newAmount,
            ConfigRevisionId expectedRevision) {
        return moneyInputValidation(subject, prestigeLevel, newAmount, expectedRevision).normalizedAmount();
    }

    @Override
    public CompletionStage<GuidedMoneyConfigurationReview> reviewMoney(
            PermissionSubject subject,
            long prestigeLevel,
            String newAmount) {
        ConfigRevisionId expectedRevision = revision(active());
        return reviewMoney(subject, prestigeLevel, newAmount, expectedRevision);
    }

    @Override
    public CompletionStage<GuidedMoneyConfigurationReview> reviewMoney(
            PermissionSubject subject,
            long prestigeLevel,
            String newAmount,
            ConfigRevisionId expectedRevision) {
        MoneyInputValidation validation =
                moneyInputValidation(subject, prestigeLevel, newAmount, expectedRevision);
        MoneyBinding binding = validation.binding();
        String normalizedAmount = validation.normalizedAmount();
        UUID draftId = administration.beginDraft(subject, "staff-gui:configuration:money");
        administration.editGuidedMoney(subject, draftId,
                new ScalingOverrideEdit("requirements.requirements." + binding.requirement().id().value()
                        + ".scaling", binding.requirementSegment(), prestigeLevel, validation.multiplier()),
                binding.pair());
        ConfigRevisionId baseRevision = revision(validation.snapshot());
        return administration.preview(subject, draftId).thenApply(preview -> {
            if (preview.stale()) {
                administration.discardDraft(subject, draftId);
                throw stale();
            }
            if (preview.validation().hasErrors()) {
                administration.discardDraft(subject, draftId);
                throw new AdministrationException("config.validation.blocked",
                        "The proposed Money change did not pass complete configuration validation.",
                        "Review the validation findings and prepare a corrected value.");
            }
            Optional<UUID> acknowledgement = preview.validation().findings().stream()
                    .anyMatch(finding -> finding.severity() == ValidationSeverity.ACKNOWLEDGEMENT_REQUIRED)
                    ? Optional.of(administration.prepareAcknowledgement(subject, draftId).acknowledgementId())
                    : Optional.empty();
            pruneReviews();
            UUID reviewId = UUID.randomUUID();
            Instant expiresAt = Instant.now(clock).plus(REVIEW_LIFETIME);
            GuidedMoneyConfigurationReview review = new GuidedMoneyConfigurationReview(reviewId, baseRevision,
                    prestigeLevel, binding.currentDisplay(), normalizedAmount, expiresAt);
            moneyReviews.put(reviewId, new MoneyReviewAuthority(subject.actor(), draftId, acknowledgement, review));
            return review;
        });
    }

    @Override
    public CompletionStage<GuidedMoneyConfigurationResult> confirmMoney(
            PermissionSubject subject,
            UUID reviewId) {
        requireMutationPermissions(subject);
        MoneyReviewAuthority authority = consumeMoneyReview(subject, reviewId);
        GuidedMoneyConfigurationReview review = authority.review();
        ConfigRevisionId current = administration.active(subject).map(value -> value.revisionId())
                .orElseThrow(this::inactive);
        if (!current.equals(review.baseRevision())) {
            administration.discardDraft(subject, authority.draftId());
            throw stale();
        }
        String reason = "Guided Money for Prestige " + review.prestigeLevel() + ": "
                + review.currentAmount() + " -> " + review.newAmount();
        CompletionStage<StoredConfigurationRevision> applied = authority.acknowledgementId().isPresent()
                ? administration.confirmAcknowledgement(subject, authority.acknowledgementId().orElseThrow(), reason)
                : administration.applyDraft(subject, authority.draftId(), Optional.of(review.baseRevision()),
                        java.util.Set.of(), reason);
        return applied.thenApply(result -> new GuidedMoneyConfigurationResult(review.baseRevision(), result.id(),
                review.prestigeLevel(), review.currentAmount(), review.newAmount()));
    }

    @Override
    public RewardAmountPage rewardAmounts(
            PermissionSubject subject,
            long prestigeLevel,
            int pageIndex,
            int pageSize) {
        requireMutationPermissions(subject);
        PrestigeLevelConfigurationView level = prestigeLevel(subject, prestigeLevel);
        if (!level.rewardAvailable() || !level.rewardEditable()) {
            throw unsupportedRewardEditor();
        }
        if (pageIndex < 0 || pageSize < 1 || pageSize > 45) {
            throw new IllegalArgumentException("Reward page and size are outside the visual editor bounds");
        }
        long first = Math.addExact(Math.multiplyExact((long) pageIndex, pageSize), 1);
        if (first > MAX_SELECTABLE_MONEY) {
            throw new AdministrationException("config.gui.reward_page.invalid",
                    "The selected Reward amount page is outside the guided range.",
                    "Return to the Rewards editor and select an available page.");
        }
        long last = Math.min(MAX_SELECTABLE_MONEY, Math.addExact(first, pageSize - 1L));
        ArrayList<String> amounts = new ArrayList<>();
        for (long amount = first; amount <= last; amount++) {
            amounts.add(Long.toString(amount));
        }
        return new RewardAmountPage(level.revision(), prestigeLevel, level.rewardAmount(), amounts, pageIndex,
                pageIndex > 0, last < MAX_SELECTABLE_MONEY);
    }

    @Override
    public GuidedNumericConfigurationInput rewardInput(PermissionSubject subject, long prestigeLevel) {
        requireMutationPermissions(subject);
        ActivePhaseFourConfiguration snapshot = active();
        requireLevel(snapshot, prestigeLevel);
        RewardBinding binding = rewardBinding(snapshot, prestigeLevel);
        if (!binding.editable()) {
            throw unsupportedRewardEditor();
        }
        return new GuidedNumericConfigurationInput(revision(snapshot), prestigeLevel, binding.amount());
    }

    @Override
    public String validateRewardInput(
            PermissionSubject subject,
            long prestigeLevel,
            String newAmount,
            ConfigRevisionId expectedRevision) {
        return rewardInputValidation(subject, prestigeLevel, newAmount, expectedRevision).normalizedAmount();
    }

    @Override
    public CompletionStage<GuidedRewardConfigurationReview> reviewReward(
            PermissionSubject subject,
            long prestigeLevel,
            String newAmount) {
        ConfigRevisionId expectedRevision = revision(active());
        return reviewReward(subject, prestigeLevel, newAmount, expectedRevision);
    }

    @Override
    public CompletionStage<GuidedRewardConfigurationReview> reviewReward(
            PermissionSubject subject,
            long prestigeLevel,
            String newAmount,
            ConfigRevisionId expectedRevision) {
        RewardInputValidation validation =
                rewardInputValidation(subject, prestigeLevel, newAmount, expectedRevision);
        RewardBinding binding = validation.binding();
        String normalizedAmount = validation.normalizedAmount();
        UUID draftId = administration.beginDraft(subject, "staff-gui:configuration:reward");
        administration.editScalar(subject, draftId,
                "rewards.rewards." + binding.reward().id().value() + ".value", normalizedAmount);
        ConfigRevisionId baseRevision = revision(validation.snapshot());
        return administration.preview(subject, draftId).thenApply(preview -> {
            if (preview.stale()) {
                administration.discardDraft(subject, draftId);
                throw stale();
            }
            if (preview.validation().hasErrors()) {
                administration.discardDraft(subject, draftId);
                throw new AdministrationException("config.validation.blocked",
                        "The proposed Reward change did not pass complete configuration validation.",
                        "Review the validation findings and prepare a corrected value.");
            }
            Optional<UUID> acknowledgement = preview.validation().findings().stream()
                    .anyMatch(finding -> finding.severity() == ValidationSeverity.ACKNOWLEDGEMENT_REQUIRED)
                    ? Optional.of(administration.prepareAcknowledgement(subject, draftId).acknowledgementId())
                    : Optional.empty();
            pruneReviews();
            UUID reviewId = UUID.randomUUID();
            Instant expiresAt = Instant.now(clock).plus(REVIEW_LIFETIME);
            GuidedRewardConfigurationReview review = new GuidedRewardConfigurationReview(reviewId, baseRevision,
                    prestigeLevel, binding.amount(), normalizedAmount, expiresAt);
            rewardReviews.put(reviewId, new RewardReviewAuthority(
                    subject.actor(), draftId, acknowledgement, review));
            return review;
        });
    }

    @Override
    public CompletionStage<GuidedRewardConfigurationResult> confirmReward(
            PermissionSubject subject,
            UUID reviewId) {
        requireMutationPermissions(subject);
        RewardReviewAuthority authority = consumeRewardReview(subject, reviewId);
        GuidedRewardConfigurationReview review = authority.review();
        ConfigRevisionId current = administration.active(subject).map(value -> value.revisionId())
                .orElseThrow(this::inactive);
        if (!current.equals(review.baseRevision())) {
            administration.discardDraft(subject, authority.draftId());
            throw stale();
        }
        String reason = "Guided Reward for Prestige " + review.prestigeLevel() + ": "
                + review.currentAmount() + " -> " + review.newAmount();
        CompletionStage<StoredConfigurationRevision> applied = authority.acknowledgementId().isPresent()
                ? administration.confirmAcknowledgement(subject, authority.acknowledgementId().orElseThrow(), reason)
                : administration.applyDraft(subject, authority.draftId(), Optional.of(review.baseRevision()),
                        java.util.Set.of(), reason);
        return applied.thenApply(result -> new GuidedRewardConfigurationResult(review.baseRevision(), result.id(),
                review.prestigeLevel(), review.currentAmount(), review.newAmount()));
    }

    @Override
    public GuidedRequirementConfigurationView requirements(PermissionSubject subject, long prestigeLevel) {
        subject.require(PhaseSixPermissions.CONFIG_VIEW);
        ActivePhaseFourConfiguration snapshot = active();
        requireLevel(snapshot, prestigeLevel);
        RequirementNode root = requirementRoot(snapshot);
        List<RequirementDefinition> definitions = leaves(root);
        long totalSkillLevels = definitions.stream().filter(CanonicalGuidedConfigurationAdministration::isTotalSkillLevel)
                .count();
        boolean complex = !isFlatRequirementTree(root);
        Optional<MoneyBinding> money = optionalMoneyBinding(snapshot, prestigeLevel);
        List<GuidedRequirementConfigurationEntry> entries = definitions.stream().map(requirement -> {
            GuidedRequirementConfigurationEntry.Kind kind = requirementKind(requirement);
            MetricValue effective = new TargetTransformer().transform(requirement.target(), requirement.scaling(),
                    requirement.catchUp(), prestigeLevel - 1, ExactDecimal.ZERO).target().lower();
            Optional<MoneyBinding> matchingMoney = money.filter(binding ->
                    binding.requirement().id().equals(requirement.id()));
            boolean editable = switch (kind) {
                case MONEY -> matchingMoney.map(MoneyBinding::editable).orElse(false);
                case TOTAL_SKILL_LEVEL -> totalSkillLevels == 1 && !complex
                        && isSimpleTotalSkillLevel(requirement);
                case OTHER -> false;
            };
            String currentTarget = matchingMoney.map(MoneyBinding::costAmount).orElse(effective.canonical());
            return new GuidedRequirementConfigurationEntry(requirement.id().value(), kind,
                    requirementDisplayName(requirement), currentTarget, editable);
        }).toList();
        return new GuidedRequirementConfigurationView(revision(snapshot), prestigeLevel, entries, complex);
    }

    @Override
    public GuidedNumericConfigurationInput totalSkillLevelInput(
            PermissionSubject subject,
            long prestigeLevel) {
        requireMutationPermissions(subject);
        ActivePhaseFourConfiguration snapshot = active();
        requireLevel(snapshot, prestigeLevel);
        TotalSkillLevelBinding binding = totalSkillLevelBinding(snapshot, prestigeLevel);
        return new GuidedNumericConfigurationInput(revision(snapshot), prestigeLevel, binding.currentTarget());
    }

    @Override
    public String validateTotalSkillLevelInput(
            PermissionSubject subject,
            long prestigeLevel,
            String newTarget,
            ConfigRevisionId expectedRevision) {
        return totalSkillLevelInputValidation(subject, prestigeLevel, newTarget, expectedRevision).normalizedTarget();
    }

    @Override
    public CompletionStage<GuidedTotalSkillLevelReview> reviewTotalSkillLevel(
            PermissionSubject subject,
            long prestigeLevel,
            String newTarget,
            ConfigRevisionId expectedRevision) {
        TotalSkillLevelInputValidation validation = totalSkillLevelInputValidation(
                subject, prestigeLevel, newTarget, expectedRevision);
        UUID draftId = administration.beginDraft(subject, "staff-gui:configuration:total-skill-level");
        administration.editScalar(subject, draftId,
                "requirements.requirements." + validation.binding().requirement().id().value() + ".target",
                validation.normalizedTarget());
        ConfigRevisionId baseRevision = revision(validation.snapshot());
        return administration.preview(subject, draftId).thenApply(preview -> {
            if (preview.stale()) {
                administration.discardDraft(subject, draftId);
                throw stale();
            }
            if (preview.validation().hasErrors()) {
                administration.discardDraft(subject, draftId);
                throw new AdministrationException("config.validation.blocked",
                        "The proposed Total Skill Level change did not pass complete configuration validation.",
                        "Review the validation findings and prepare a corrected value.");
            }
            Optional<UUID> acknowledgement = preview.validation().findings().stream()
                    .anyMatch(finding -> finding.severity() == ValidationSeverity.ACKNOWLEDGEMENT_REQUIRED)
                    ? Optional.of(administration.prepareAcknowledgement(subject, draftId).acknowledgementId())
                    : Optional.empty();
            pruneReviews();
            UUID reviewId = UUID.randomUUID();
            GuidedTotalSkillLevelReview review = new GuidedTotalSkillLevelReview(reviewId, baseRevision,
                    prestigeLevel, validation.binding().currentTarget(), validation.normalizedTarget(),
                    Instant.now(clock).plus(REVIEW_LIFETIME));
            totalSkillLevelReviews.put(reviewId, new TotalSkillLevelReviewAuthority(
                    subject.actor(), draftId, acknowledgement, review));
            return review;
        });
    }

    @Override
    public CompletionStage<GuidedTotalSkillLevelResult> confirmTotalSkillLevel(
            PermissionSubject subject,
            UUID reviewId) {
        requireMutationPermissions(subject);
        TotalSkillLevelReviewAuthority authority = consumeTotalSkillLevelReview(subject, reviewId);
        GuidedTotalSkillLevelReview review = authority.review();
        ConfigRevisionId current = administration.active(subject).map(value -> value.revisionId())
                .orElseThrow(this::inactive);
        if (!current.equals(review.baseRevision())) {
            administration.discardDraft(subject, authority.draftId());
            throw stale();
        }
        String reason = "Guided Total Skill Level for Prestige " + review.prestigeLevel() + ": "
                + review.currentTarget() + " -> " + review.newTarget();
        CompletionStage<StoredConfigurationRevision> applied = authority.acknowledgementId().isPresent()
                ? administration.confirmAcknowledgement(subject, authority.acknowledgementId().orElseThrow(), reason)
                : administration.applyDraft(subject, authority.draftId(), Optional.of(review.baseRevision()),
                        java.util.Set.of(), reason);
        return applied.thenApply(result -> new GuidedTotalSkillLevelResult(
                review.baseRevision(), result.id(), review.prestigeLevel(),
                review.currentTarget(), review.newTarget()));
    }

    @Override
    public GuidedScalingConfigurationView scaling(PermissionSubject subject, long prestigeLevel) {
        subject.require(PhaseSixPermissions.CONFIG_VIEW);
        ActivePhaseFourConfiguration snapshot = active();
        requireLevel(snapshot, prestigeLevel);
        return scalingBinding(subject, snapshot, prestigeLevel).view();
    }

    @Override
    public GuidedNumericConfigurationInput scalingInput(
            PermissionSubject subject,
            long prestigeLevel,
            GuidedScalingParameter parameter) {
        requireMutationPermissions(subject);
        ActivePhaseFourConfiguration snapshot = active();
        requireLevel(snapshot, prestigeLevel);
        ScalingBinding binding = scalingBinding(subject, snapshot, prestigeLevel);
        requireEditableScaling(binding, parameter);
        return new GuidedNumericConfigurationInput(
                revision(snapshot), prestigeLevel, binding.view().value(parameter));
    }

    @Override
    public String validateScalingInput(
            PermissionSubject subject,
            long prestigeLevel,
            GuidedScalingParameter parameter,
            String newValue,
            ConfigRevisionId expectedRevision) {
        return scalingInputValidation(subject, prestigeLevel, parameter, newValue, expectedRevision)
                .normalizedValue();
    }

    @Override
    public CompletionStage<GuidedScalingConfigurationReview> reviewScaling(
            PermissionSubject subject,
            long prestigeLevel,
            GuidedScalingParameter parameter,
            String newValue,
            ConfigRevisionId expectedRevision) {
        ScalingInputValidation validation = scalingInputValidation(
                subject, prestigeLevel, parameter, newValue, expectedRevision);
        ScalingBinding binding = validation.binding();
        UUID draftId = administration.beginDraft(subject, "staff-gui:configuration:scaling");
        administration.editGuidedScalingParameter(subject, draftId, new ScalingParameterEdit(
                "requirements.requirements." + binding.requirement().id().value() + ".scaling",
                binding.segmentIndex(), parameter, validation.normalizedValue()), binding.pair());
        ConfigRevisionId baseRevision = revision(validation.snapshot());
        return administration.preview(subject, draftId).thenApply(preview -> {
            if (preview.stale()) {
                administration.discardDraft(subject, draftId);
                throw stale();
            }
            if (preview.validation().hasErrors()) {
                administration.discardDraft(subject, draftId);
                throw new AdministrationException("config.validation.blocked",
                        "The proposed Scaling change did not pass complete configuration validation.",
                        "Review the validation findings and prepare a corrected value.");
            }
            Optional<UUID> acknowledgement = preview.validation().findings().stream()
                    .anyMatch(finding -> finding.severity() == ValidationSeverity.ACKNOWLEDGEMENT_REQUIRED)
                    ? Optional.of(administration.prepareAcknowledgement(subject, draftId).acknowledgementId())
                    : Optional.empty();
            pruneReviews();
            UUID reviewId = UUID.randomUUID();
            Instant expiresAt = Instant.now(clock).plus(REVIEW_LIFETIME);
            GuidedScalingConfigurationReview review = new GuidedScalingConfigurationReview(
                    reviewId, baseRevision, prestigeLevel, parameter, binding.view().value(parameter),
                    validation.normalizedValue(), expiresAt);
            scalingReviews.put(reviewId, new ScalingReviewAuthority(
                    subject.actor(), draftId, acknowledgement, review));
            return review;
        });
    }

    @Override
    public CompletionStage<GuidedScalingConfigurationResult> confirmScaling(
            PermissionSubject subject,
            UUID reviewId) {
        requireMutationPermissions(subject);
        ScalingReviewAuthority authority = consumeScalingReview(subject, reviewId);
        GuidedScalingConfigurationReview review = authority.review();
        ConfigRevisionId current = administration.active(subject).map(value -> value.revisionId())
                .orElseThrow(this::inactive);
        if (!current.equals(review.baseRevision())) {
            administration.discardDraft(subject, authority.draftId());
            throw stale();
        }
        String reason = "Guided " + scalingParameterName(review.parameter()) + " for Prestige "
                + review.prestigeLevel() + ": " + review.currentValue() + " -> " + review.newValue();
        CompletionStage<StoredConfigurationRevision> applied = authority.acknowledgementId().isPresent()
                ? administration.confirmAcknowledgement(subject, authority.acknowledgementId().orElseThrow(), reason)
                : administration.applyDraft(subject, authority.draftId(), Optional.of(review.baseRevision()),
                        java.util.Set.of(), reason);
        return applied.thenApply(result -> new GuidedScalingConfigurationResult(
                review.baseRevision(), result.id(), review.prestigeLevel(), review.parameter(),
                review.currentValue(), review.newValue()));
    }

    @Override
    public GuidedNumericConfigurationInput scalingOverrideInput(
            PermissionSubject subject,
            long prestigeLevel) {
        requireMutationPermissions(subject);
        ActivePhaseFourConfiguration snapshot = active();
        requireLevel(snapshot, prestigeLevel);
        ScalingBinding binding = scalingBinding(subject, snapshot, prestigeLevel);
        requireManageableScalingOverride(binding);
        return new GuidedNumericConfigurationInput(revision(snapshot), prestigeLevel,
                binding.view().overrideValue().orElse(binding.view().effectiveValue()));
    }

    @Override
    public String validateScalingOverrideInput(
            PermissionSubject subject,
            long prestigeLevel,
            String newValue,
            ConfigRevisionId expectedRevision) {
        return scalingOverrideInputValidation(subject, prestigeLevel, newValue, expectedRevision).normalizedValue();
    }

    @Override
    public CompletionStage<GuidedScalingOverrideReview> reviewScalingOverride(
            PermissionSubject subject,
            long prestigeLevel,
            String newValue,
            ConfigRevisionId expectedRevision) {
        ScalingOverrideInputValidation validation = scalingOverrideInputValidation(
                subject, prestigeLevel, newValue, expectedRevision);
        ScalingBinding binding = validation.binding();
        String sourceSurface = binding.view().overrideValue().isPresent()
                ? "staff-gui:configuration:scaling-override-edit"
                : "staff-gui:configuration:scaling-override-add";
        UUID draftId = administration.beginDraft(subject, sourceSurface);
        administration.editGuidedScalingOverride(subject, draftId, new ScalingOverrideEdit(
                scalingPath(binding), binding.segmentIndex(), prestigeLevel, validation.normalizedValue()),
                binding.pair());
        return prepareScalingOverrideReview(subject, validation.snapshot(), binding, draftId,
                Optional.of(validation.normalizedValue()));
    }

    @Override
    public CompletionStage<GuidedScalingOverrideReview> reviewScalingOverrideRemoval(
            PermissionSubject subject,
            long prestigeLevel,
            ConfigRevisionId expectedRevision) {
        requireMutationPermissions(subject);
        ActivePhaseFourConfiguration snapshot = active();
        requireExpectedRevision(snapshot, expectedRevision);
        requireLevel(snapshot, prestigeLevel);
        ScalingBinding binding = scalingBinding(subject, snapshot, prestigeLevel);
        requireExistingScalingOverride(binding);
        UUID draftId = administration.beginDraft(subject, "staff-gui:configuration:scaling-override-removal");
        administration.removeGuidedScalingOverride(subject, draftId, new ScalingOverrideRemoval(
                scalingPath(binding), binding.segmentIndex(), prestigeLevel), binding.pair());
        return prepareScalingOverrideReview(subject, snapshot, binding, draftId, Optional.empty());
    }

    private CompletionStage<GuidedScalingOverrideReview> prepareScalingOverrideReview(
            PermissionSubject subject,
            ActivePhaseFourConfiguration snapshot,
            ScalingBinding binding,
            UUID draftId,
            Optional<String> newOverride) {
        Optional<String> currentOverride = binding.view().overrideValue();
        Optional<String> currentEffective = Optional.of(binding.view().effectiveValue());
        Optional<String> newEffective = Optional.of(effectiveValueForOverride(binding, newOverride));
        ConfigRevisionId baseRevision = revision(snapshot);
        return administration.preview(subject, draftId).thenApply(preview -> {
            if (preview.stale()) {
                administration.discardDraft(subject, draftId);
                throw stale();
            }
            if (preview.validation().hasErrors()) {
                administration.discardDraft(subject, draftId);
                throw new AdministrationException("config.validation.blocked",
                        "The proposed Override change did not pass complete configuration validation.",
                        "Review the validation findings and prepare a corrected Override change.");
            }
            Optional<UUID> acknowledgement = preview.validation().findings().stream()
                    .anyMatch(finding -> finding.severity() == ValidationSeverity.ACKNOWLEDGEMENT_REQUIRED)
                    ? Optional.of(administration.prepareAcknowledgement(subject, draftId).acknowledgementId())
                    : Optional.empty();
            pruneReviews();
            UUID reviewId = UUID.randomUUID();
            Instant expiresAt = Instant.now(clock).plus(REVIEW_LIFETIME);
            GuidedScalingOverrideReview review = new GuidedScalingOverrideReview(
                    reviewId, baseRevision, binding.view().prestigeLevel(), currentOverride, newOverride,
                    currentEffective, newEffective, expiresAt);
            scalingOverrideReviews.put(reviewId, new ScalingOverrideReviewAuthority(
                    subject.actor(), draftId, acknowledgement, review));
            return review;
        });
    }

    @Override
    public CompletionStage<GuidedScalingOverrideResult> confirmScalingOverride(
            PermissionSubject subject,
            UUID reviewId) {
        requireMutationPermissions(subject);
        ScalingOverrideReviewAuthority authority = consumeScalingOverrideReview(subject, reviewId);
        GuidedScalingOverrideReview review = authority.review();
        ConfigRevisionId current = administration.active(subject).map(value -> value.revisionId())
                .orElseThrow(this::inactive);
        if (!current.equals(review.baseRevision())) {
            administration.discardDraft(subject, authority.draftId());
            throw stale();
        }
        String reason;
        if (review.removal()) {
            reason = "Guided Scaling Override removal for Prestige " + review.prestigeLevel() + ": "
                    + review.currentOverride().orElseThrow() + " -> inherited";
        } else if (review.addition()) {
            reason = "Guided Scaling Override addition for Prestige " + review.prestigeLevel() + ": inherited -> "
                    + review.newOverride().orElseThrow();
        } else {
            reason = "Guided Scaling Override edit for Prestige " + review.prestigeLevel() + ": "
                    + review.currentOverride().orElseThrow() + " -> " + review.newOverride().orElseThrow();
        }
        CompletionStage<StoredConfigurationRevision> applied = authority.acknowledgementId().isPresent()
                ? administration.confirmAcknowledgement(subject, authority.acknowledgementId().orElseThrow(), reason)
                : administration.applyDraft(subject, authority.draftId(), Optional.of(review.baseRevision()),
                        java.util.Set.of(), reason);
        return applied.thenApply(result -> new GuidedScalingOverrideResult(
                review.baseRevision(), result.id(), review.prestigeLevel(), review.currentOverride(),
                review.newOverride()));
    }

    private MoneyBinding moneyBinding(ActivePhaseFourConfiguration snapshot, long prestigeLevel) {
        PhaseThreeConfiguration phaseThree = snapshot.priorPhases().phaseThree().configuration();
        PhaseFourConfiguration phaseFour = snapshot.phaseFour().configuration();
        RequirementNode root = phaseFour.prestige().requirementTreeId().map(phaseThree.trees()::get)
                .orElseThrow(() -> new AdministrationException("config.gui.money.unavailable",
                        "The active Prestige configuration has no guided requirement tree.",
                        "Inspect the active configuration before editing Money."));
        List<RequirementDefinition> requirements = leaves(root).stream()
                .filter(value -> value.target().upper().isEmpty())
                .filter(value -> value.target().lower().type() == MetricValueType.CURRENCY_AMOUNT)
                .filter(value -> value.providerId().value().equals(VAULT_BALANCE_PROVIDER))
                .filter(value -> value.metricId().value().equals("balance"))
                .toList();
        List<CostDefinition> costs = phaseFour.prestige().costIds().stream().map(phaseThree.costs()::get)
                .filter(Objects::nonNull)
                .filter(value -> value.amount().type() == MetricValueType.CURRENCY_AMOUNT)
                .filter(value -> value.providerId().value().equals(VAULT_ECONOMY_COST_PROVIDER))
                .filter(value -> value.type().equals(VAULT_ECONOMY_COST_TYPE))
                .toList();
        if (requirements.size() != 1 || costs.size() != 1) {
            throw unsupportedMoneyEditor();
        }
        RequirementDefinition requirement = requirements.getFirst();
        CostDefinition cost = costs.getFirst();
        MetricValue effectiveRequirement = new TargetTransformer().transform(requirement.target(),
                requirement.scaling(), requirement.catchUp(), prestigeLevel - 1, ExactDecimal.ZERO).target().lower();
        CostDefinition effectiveCost = phaseFour.valueScaling().scale(cost, prestigeLevel);
        int segment = requirementSegment(requirement, prestigeLevel);
        var costScaling = phaseFour.valueScaling().costs().get(cost.id());
        boolean existingPair = costScaling != null
                && requirement.target().lower().asNumber().compareTo(cost.amount().asNumber()) == 0
                && requirement.scaling().segments().equals(costScaling.segments());
        boolean editable = segment >= 0 && requirement.target().lower().asNumber().signum() > 0
                && !requirement.catchUp().enabled()
                && (costScaling == null || existingPair);
        String scaling = scalingDescription(requirement, prestigeLevel);
        return new MoneyBinding(requirement, cost, canonical(effectiveRequirement.asNumber()),
                canonical(effectiveCost.amount().asNumber()), canonical(requirement.target().lower().asNumber()),
                segment, scaling, editable, guidedMoneyPair(requirement, cost));
    }

    private Optional<MoneyBinding> optionalMoneyBinding(
            ActivePhaseFourConfiguration snapshot,
            long prestigeLevel) {
        try {
            return Optional.of(moneyBinding(snapshot, prestigeLevel));
        } catch (AdministrationException exception) {
            if (exception.code().equals("config.gui.money.unavailable")
                    || exception.code().equals("config.gui.money.unsupported")) {
                return Optional.empty();
            }
            throw exception;
        }
    }

    private RequirementNode requirementRoot(ActivePhaseFourConfiguration snapshot) {
        PhaseThreeConfiguration phaseThree = snapshot.priorPhases().phaseThree().configuration();
        return snapshot.phaseFour().configuration().prestige().requirementTreeId().map(phaseThree.trees()::get)
                .orElseThrow(() -> new AdministrationException("config.gui.requirements.unavailable",
                        "The active Prestige configuration has no guided requirement tree.",
                        "Inspect the active configuration before editing Requirements."));
    }

    private TotalSkillLevelBinding totalSkillLevelBinding(
            ActivePhaseFourConfiguration snapshot,
            long prestigeLevel) {
        RequirementNode root = requirementRoot(snapshot);
        List<RequirementDefinition> matches = leaves(root).stream()
                .filter(CanonicalGuidedConfigurationAdministration::isTotalSkillLevel)
                .toList();
        if (matches.size() != 1 || !isFlatRequirementTree(root)
                || !isSimpleTotalSkillLevel(matches.getFirst())) {
            throw unsupportedTotalSkillLevelEditor();
        }
        RequirementDefinition requirement = matches.getFirst();
        MetricValue effective = new TargetTransformer().transform(requirement.target(), requirement.scaling(),
                requirement.catchUp(), prestigeLevel - 1, ExactDecimal.ZERO).target().lower();
        return new TotalSkillLevelBinding(requirement, effective.canonical());
    }

    private static boolean isTotalSkillLevel(RequirementDefinition requirement) {
        return requirement.providerId().value().equals(MCMO_PROVIDER)
                && requirement.metricId().value().equals(TOTAL_LEVEL_METRIC);
    }

    private static boolean isSimpleTotalSkillLevel(RequirementDefinition requirement) {
        MetricValueType type = requirement.target().lower().type();
        return isTotalSkillLevel(requirement)
                && requirement.target().upper().isEmpty()
                && (type == MetricValueType.COUNT || type == MetricValueType.INTEGER)
                && requirement.scaling().strategy() == ScalingStrategy.NONE
                && requirement.scaling().segments().isEmpty()
                && !requirement.catchUp().enabled()
                && requirement.filters().isEmpty();
    }

    private static boolean isFlatRequirementTree(RequirementNode root) {
        return root instanceof RequirementLeaf || root instanceof RequirementGroup group
                && group.children().stream().allMatch(child -> child.node() instanceof RequirementLeaf);
    }

    private static GuidedRequirementConfigurationEntry.Kind requirementKind(RequirementDefinition requirement) {
        if (isTotalSkillLevel(requirement)) {
            return GuidedRequirementConfigurationEntry.Kind.TOTAL_SKILL_LEVEL;
        }
        if (requirement.providerId().value().equals(VAULT_BALANCE_PROVIDER)
                && requirement.metricId().value().equals("balance")) {
            return GuidedRequirementConfigurationEntry.Kind.MONEY;
        }
        return GuidedRequirementConfigurationEntry.Kind.OTHER;
    }

    private static String requirementDisplayName(RequirementDefinition requirement) {
        return switch (requirementKind(requirement)) {
            case MONEY -> "Money";
            case TOTAL_SKILL_LEVEL -> "Total Skill Level";
            case OTHER -> humanize(requirement.metricId().value());
        };
    }

    private ScalingBinding scalingBinding(
            PermissionSubject subject,
            ActivePhaseFourConfiguration snapshot,
            long prestigeLevel) {
        MoneyBinding money = moneyBinding(snapshot, prestigeLevel);
        if (!money.editable()) {
            throw unsupportedScalingEditor();
        }
        RequirementDefinition requirement = money.requirement();
        int segmentIndex = requirementSegment(requirement, prestigeLevel);
        if (segmentIndex < 0) {
            throw unsupportedScalingEditor();
        }
        PrestigeScalingSegment segment = requirement.scaling().segments().get(segmentIndex);
        MetricValue effective = new TargetTransformer().transform(requirement.target(), requirement.scaling(),
                requirement.catchUp(), prestigeLevel - 1, ExactDecimal.ZERO).target().lower();
        boolean complex = requirement.scaling().segments().size() != 1
                || segment.transition() == SegmentTransition.CONTINUE
                || segment.mode() == SegmentScalingMode.MANUAL;
        Set<GuidedScalingParameter> editable = java.util.Arrays.stream(GuidedScalingParameter.values())
                .filter(ignored -> !complex && segment.mode() == SegmentScalingMode.LINEAR)
                .filter(parameter -> explicitScalingParameter(
                        subject, snapshot, requirement, segmentIndex, parameter))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        Optional<String> override = Optional.ofNullable(segment.overrides().get(prestigeLevel))
                .map(value -> canonical(value.asBigDecimal()));
        boolean overrideManageable = !complex && segment.mode() == SegmentScalingMode.LINEAR;
        GuidedScalingConfigurationView view = new GuidedScalingConfigurationView(
                revision(snapshot), prestigeLevel, segment.mode(), canonical(segment.base().asBigDecimal()),
                canonical(segment.rate().asBigDecimal()), canonical(effective.asNumber()), override,
                editable, complex, overrideManageable);
        return new ScalingBinding(requirement, segmentIndex, view, money.pair());
    }

    private boolean explicitScalingParameter(
            PermissionSubject subject,
            ActivePhaseFourConfiguration snapshot,
            RequirementDefinition requirement,
            int segmentIndex,
            GuidedScalingParameter parameter) {
        return administration.active(subject)
                .filter(value -> value.revisionId().equals(revision(snapshot)))
                .map(value -> value.compiled().documents().get("requirements.yml"))
                .filter(Objects::nonNull)
                .map(LosslessYamlDocument::parse)
                .map(document -> document.contains(YamlPath.document(0)
                        .key("requirements").key(requirement.id().value()).key("scaling")
                        .key("segments").index(segmentIndex).key(parameter.yamlField())))
                .orElse(false);
    }

    private static GuidedMoneyScalingPair guidedMoneyPair(
            RequirementDefinition requirement,
            CostDefinition cost) {
        LinkedHashMap<String, Object> profile = new LinkedHashMap<>();
        profile.put("segments", requirement.scaling().segments().stream()
                .map(CanonicalGuidedConfigurationAdministration::scalingSegmentStructure)
                .toList());
        String requirementPath = "requirements.requirements." + requirement.id().value() + ".scaling";
        return new GuidedMoneyScalingPair(
                requirement.id().value(),
                cost.id().value(),
                requirementPath,
                "requirements.costs." + cost.id().value() + ".amount",
                canonical(requirement.target().lower().asNumber()),
                new StructuredConfigurationValue(profile));
    }

    private static Map<String, Object> scalingSegmentStructure(PrestigeScalingSegment segment) {
        LinkedHashMap<String, Object> fields = new LinkedHashMap<>();
        fields.put("start-prestige", segment.startLevel());
        fields.put("end-prestige", segment.endLevel().isPresent()
                ? segment.endLevel().getAsLong() : "unlimited");
        fields.put("mode", segment.mode().name());
        fields.put("transition", segment.transition().name());
        fields.put("base", segment.base().asBigDecimal());
        fields.put("rate", segment.rate().asBigDecimal());
        fields.put("rounding", segment.rounding().name());
        fields.put("quantum", segment.roundingQuantum().asBigDecimal());
        segment.floor().ifPresent(value -> fields.put("floor", value.asBigDecimal()));
        segment.cap().ifPresent(value -> fields.put("cap", value.asBigDecimal()));
        if (!segment.overrides().isEmpty()) {
            LinkedHashMap<String, Object> overrides = new LinkedHashMap<>();
            segment.overrides().forEach((level, value) ->
                    overrides.put(Long.toString(level), value.asBigDecimal()));
            fields.put("overrides", overrides);
        }
        return fields;
    }

    private RewardBinding rewardBinding(ActivePhaseFourConfiguration snapshot, long prestigeLevel) {
        PhaseThreeConfiguration phaseThree = snapshot.priorPhases().phaseThree().configuration();
        PhaseFourConfiguration phaseFour = snapshot.phaseFour().configuration();
        if (phaseFour.prestige().rewardIds().size() != 1) {
            throw unsupportedRewardEditor();
        }
        RewardDefinition reward = Optional.ofNullable(
                phaseThree.rewards().get(phaseFour.prestige().rewardIds().getFirst()))
                .orElseThrow(() -> new AdministrationException("config.gui.reward.unavailable",
                        "The active Prestige configuration has no available guided Reward.",
                        "Inspect the active configuration before editing Rewards."));
        if (reward.value().type() != MetricValueType.CURRENCY_AMOUNT
                || !reward.providerId().value().equals(VAULT_ECONOMY_REWARD_PROVIDER)
                || !reward.type().equals(VAULT_ECONOMY_REWARD_TYPE)) {
            throw unsupportedRewardEditor();
        }
        RewardDefinition effective = phaseFour.valueScaling().scale(reward, prestigeLevel);
        boolean editable = !phaseFour.valueScaling().rewards().containsKey(reward.id());
        return new RewardBinding(reward, canonical(effective.value().asNumber()), editable);
    }

    private static List<RequirementDefinition> leaves(RequirementNode node) {
        if (node instanceof RequirementLeaf leaf) {
            return List.of(leaf.definition());
        }
        RequirementGroup group = (RequirementGroup) node;
        return group.children().stream().flatMap(child -> leaves(child.node()).stream()).toList();
    }

    private static int requirementSegment(RequirementDefinition requirement, long prestigeLevel) {
        List<PrestigeScalingSegment> segments = requirement.scaling().segments();
        for (int index = 0; index < segments.size(); index++) {
            if (segments.get(index).contains(prestigeLevel)) {
                return index;
            }
        }
        return -1;
    }

    private static String scalingDescription(RequirementDefinition requirement, long prestigeLevel) {
        int index = requirementSegment(requirement, prestigeLevel);
        if (index >= 0) {
            PrestigeScalingSegment segment = requirement.scaling().segments().get(index);
            String name = humanize(segment.mode().name());
            return segment.overrides().containsKey(prestigeLevel) ? name + " with level override" : name;
        }
        return requirement.scaling().strategy() == ScalingStrategy.NONE
                ? "None" : humanize(requirement.scaling().strategy().name());
    }

    private static String humanize(String value) {
        String lower = value.toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private static String currentAmount(PrestigeLevelConfigurationView view) {
        return view.moneyRequirement().equals(view.moneyCost())
                ? view.moneyRequirement() : view.moneyRequirement() + " requirement / " + view.moneyCost() + " cost";
    }

    private static String positiveCurrency(String value) {
        MetricValue amount;
        try {
            if (value == null || !PLAIN_DECIMAL.matcher(value).matches()) {
                throw new IllegalArgumentException("Money amount must use plain decimal syntax");
            }
            amount = MetricValue.parse(MetricValueType.CURRENCY_AMOUNT, value);
        } catch (IllegalArgumentException exception) {
            throw new AdministrationException("config.gui.money.invalid", "Money amount is not a valid exact value.",
                    "Enter a plain number between 1 and " + MAX_SELECTABLE_MONEY + ".");
        }
        if (amount.asNumber().signum() <= 0 || amount.asNumber().compareTo(BigDecimal.valueOf(MAX_SELECTABLE_MONEY)) > 0) {
            throw new AdministrationException("config.gui.money.invalid",
                    "Money amount must be between 1 and " + MAX_SELECTABLE_MONEY + ".",
                    "Enter a value inside the guided Money range.");
        }
        return amount.canonical();
    }

    private static String positiveRewardCurrency(String value) {
        MetricValue amount;
        try {
            if (value == null || !PLAIN_DECIMAL.matcher(value).matches()) {
                throw new IllegalArgumentException("Reward amount must use plain decimal syntax");
            }
            amount = MetricValue.parse(MetricValueType.CURRENCY_AMOUNT,
                    value);
        } catch (IllegalArgumentException exception) {
            throw new AdministrationException("config.gui.reward.invalid",
                    "Reward amount is not a valid exact value.",
                    "Enter a plain number between 1 and " + MAX_SELECTABLE_MONEY + ".");
        }
        if (amount.asNumber().signum() <= 0
                || amount.asNumber().compareTo(BigDecimal.valueOf(MAX_SELECTABLE_MONEY)) > 0) {
            throw new AdministrationException("config.gui.reward.invalid",
                    "Reward amount must be between 1 and " + MAX_SELECTABLE_MONEY + ".",
                    "Enter a value inside the guided Reward range.");
        }
        return amount.canonical();
    }

    private static String nonNegativeCount(String value) {
        try {
            if (value == null || !PLAIN_COUNT.matcher(value).matches()) {
                throw new IllegalArgumentException("Count must use non-negative integer syntax");
            }
            return MetricValue.parse(MetricValueType.COUNT, value).canonical();
        } catch (IllegalArgumentException exception) {
            throw new AdministrationException("config.gui.total_skill_level.invalid",
                    "Total Skill Level must be a non-negative whole number.",
                    "Enter 0 or a higher whole number.");
        }
    }

    private static String nonNegativeScalingValue(GuidedScalingParameter parameter, String value) {
        BigDecimal decimal;
        try {
            if (value == null || !PLAIN_DECIMAL.matcher(value).matches()) {
                throw new IllegalArgumentException("Scaling value must use plain decimal syntax");
            }
            decimal = ExactDecimal.parse(value).asBigDecimal();
        } catch (IllegalArgumentException exception) {
            throw invalidScalingValue(parameter);
        }
        if (decimal.signum() < 0 || decimal.abs().compareTo(ScalingProfile.MAX_MAGNITUDE) > 0
                || decimal.precision() > ScalingProfile.MAX_PARAMETER_PRECISION
                || Math.abs((long) decimal.scale()) > ScalingProfile.MAX_PARAMETER_PRECISION) {
            throw invalidScalingValue(parameter);
        }
        return canonical(decimal);
    }

    private static String exactMultiplier(String amount, String requirementBase) {
        try {
            return canonical(new BigDecimal(amount).divide(new BigDecimal(requirementBase)));
        } catch (ArithmeticException exception) {
            throw new AdministrationException("config.gui.money.non_terminating",
                    "This amount cannot be represented exactly by the active scaling profile.",
                    "Choose another exact amount or use the advanced configuration workflow.");
        }
    }

    private static String canonical(BigDecimal value) {
        BigDecimal stripped = value.stripTrailingZeros();
        return stripped.signum() == 0 ? "0" : stripped.toPlainString();
    }

    private MoneyInputValidation moneyInputValidation(
            PermissionSubject subject,
            long prestigeLevel,
            String newAmount,
            ConfigRevisionId expectedRevision) {
        requireMutationPermissions(subject);
        ActivePhaseFourConfiguration snapshot = active();
        requireExpectedRevision(snapshot, expectedRevision);
        requireLevel(snapshot, prestigeLevel);
        MoneyBinding binding = moneyBinding(snapshot, prestigeLevel);
        if (!binding.editable()) {
            throw unsupportedMoneyEditor();
        }
        String normalizedAmount = positiveCurrency(newAmount);
        return new MoneyInputValidation(snapshot, binding, normalizedAmount,
                exactMultiplier(normalizedAmount, binding.requirementBase()));
    }

    private RewardInputValidation rewardInputValidation(
            PermissionSubject subject,
            long prestigeLevel,
            String newAmount,
            ConfigRevisionId expectedRevision) {
        requireMutationPermissions(subject);
        ActivePhaseFourConfiguration snapshot = active();
        requireExpectedRevision(snapshot, expectedRevision);
        requireLevel(snapshot, prestigeLevel);
        RewardBinding binding = rewardBinding(snapshot, prestigeLevel);
        if (!binding.editable()) {
            throw unsupportedRewardEditor();
        }
        return new RewardInputValidation(snapshot, binding, positiveRewardCurrency(newAmount));
    }

    private TotalSkillLevelInputValidation totalSkillLevelInputValidation(
            PermissionSubject subject,
            long prestigeLevel,
            String newTarget,
            ConfigRevisionId expectedRevision) {
        requireMutationPermissions(subject);
        ActivePhaseFourConfiguration snapshot = active();
        requireExpectedRevision(snapshot, expectedRevision);
        requireLevel(snapshot, prestigeLevel);
        return new TotalSkillLevelInputValidation(snapshot, totalSkillLevelBinding(snapshot, prestigeLevel),
                nonNegativeCount(newTarget));
    }

    private ScalingInputValidation scalingInputValidation(
            PermissionSubject subject,
            long prestigeLevel,
            GuidedScalingParameter parameter,
            String newValue,
            ConfigRevisionId expectedRevision) {
        requireMutationPermissions(subject);
        ActivePhaseFourConfiguration snapshot = active();
        requireExpectedRevision(snapshot, expectedRevision);
        requireLevel(snapshot, prestigeLevel);
        ScalingBinding binding = scalingBinding(subject, snapshot, prestigeLevel);
        requireEditableScaling(binding, parameter);
        return new ScalingInputValidation(snapshot, binding, nonNegativeScalingValue(parameter, newValue));
    }

    private ScalingOverrideInputValidation scalingOverrideInputValidation(
            PermissionSubject subject,
            long prestigeLevel,
            String newValue,
            ConfigRevisionId expectedRevision) {
        requireMutationPermissions(subject);
        ActivePhaseFourConfiguration snapshot = active();
        requireExpectedRevision(snapshot, expectedRevision);
        requireLevel(snapshot, prestigeLevel);
        ScalingBinding binding = scalingBinding(subject, snapshot, prestigeLevel);
        requireManageableScalingOverride(binding);
        return new ScalingOverrideInputValidation(snapshot, binding, nonNegativeScalingOverride(newValue));
    }

    private static String nonNegativeScalingOverride(String value) {
        BigDecimal decimal;
        try {
            if (value == null || !PLAIN_DECIMAL.matcher(value).matches()) {
                throw new IllegalArgumentException("Scaling Override must use plain decimal syntax");
            }
            decimal = ExactDecimal.parse(value).asBigDecimal();
        } catch (IllegalArgumentException exception) {
            throw invalidScalingOverride();
        }
        if (decimal.signum() < 0 || decimal.abs().compareTo(ScalingProfile.MAX_MAGNITUDE) > 0
                || decimal.precision() > ScalingProfile.MAX_PARAMETER_PRECISION
                || Math.abs((long) decimal.scale()) > ScalingProfile.MAX_PARAMETER_PRECISION) {
            throw invalidScalingOverride();
        }
        return canonical(decimal);
    }

    private static void requireManageableScalingOverride(ScalingBinding binding) {
        if (!binding.view().overrideManageable()) {
            throw unsupportedScalingEditor();
        }
    }

    private static void requireExistingScalingOverride(ScalingBinding binding) {
        requireManageableScalingOverride(binding);
        if (binding.view().overrideValue().isEmpty()) {
            throw unsupportedScalingEditor();
        }
    }

    private static String scalingPath(ScalingBinding binding) {
        return "requirements.requirements." + binding.requirement().id().value() + ".scaling";
    }

    private static String effectiveValueForOverride(ScalingBinding binding, Optional<String> override) {
        RequirementDefinition requirement = binding.requirement();
        ScalingProfile currentProfile = requirement.scaling();
        PrestigeScalingSegment currentSegment = currentProfile.segments().get(binding.segmentIndex());
        TreeMap<Long, ExactDecimal> overrides = new TreeMap<>(currentSegment.overrides());
        if (override.isPresent()) {
            overrides.put(binding.view().prestigeLevel(), ExactDecimal.parse(override.orElseThrow()));
        } else {
            overrides.remove(binding.view().prestigeLevel());
        }
        PrestigeScalingSegment replacement = new PrestigeScalingSegment(
                currentSegment.startLevel(), currentSegment.endLevel(), currentSegment.mode(),
                currentSegment.transition(), currentSegment.base(), currentSegment.rate(), currentSegment.rounding(),
                currentSegment.roundingQuantum(), currentSegment.floor(), currentSegment.cap(), overrides);
        ArrayList<PrestigeScalingSegment> segments = new ArrayList<>(currentProfile.segments());
        segments.set(binding.segmentIndex(), replacement);
        ScalingProfile candidate = new ScalingProfile(
                currentProfile.strategy(), currentProfile.parameter(), currentProfile.stepMultipliers(),
                currentProfile.rounding(), currentProfile.roundingQuantum(), segments);
        MetricValue effective = new TargetTransformer().transform(
                requirement.target(), candidate, requirement.catchUp(),
                binding.view().prestigeLevel() - 1, ExactDecimal.ZERO).target().lower();
        return canonical(effective.asNumber());
    }

    private static void requireEditableScaling(ScalingBinding binding, GuidedScalingParameter parameter) {
        Objects.requireNonNull(parameter, "scaling parameter");
        if (!binding.view().editable(parameter)) {
            throw unsupportedScalingEditor();
        }
    }

    private static void requireExpectedRevision(
            ActivePhaseFourConfiguration snapshot,
            ConfigRevisionId expectedRevision) {
        if (!revision(snapshot).equals(Objects.requireNonNull(expectedRevision, "expected revision"))) {
            throw stale();
        }
    }

    private static void requireMutationPermissions(PermissionSubject subject) {
        subject.require(PhaseSixPermissions.CONFIG_EDIT);
        subject.require(PhaseSixPermissions.CONFIG_APPLY);
    }

    private static void requireLevel(ActivePhaseFourConfiguration snapshot, long prestigeLevel) {
        if (prestigeLevel < 1 || prestigeLevel > MAX_SELECTABLE_LEVEL
                || snapshot.phaseFour().configuration().prestige().limit().maximum().isPresent()
                && prestigeLevel > snapshot.phaseFour().configuration().prestige().limit().maximum().getAsLong()) {
            throw new AdministrationException("config.gui.level.invalid",
                    "The selected Prestige level is outside the configured range.",
                    "Return to Prestige Levels and select an available level.");
        }
    }

    private static ConfigRevisionId revision(ActivePhaseFourConfiguration snapshot) {
        return snapshot.phaseFour().revisionId();
    }

    private ActivePhaseFourConfiguration active() {
        return active.get().orElseThrow(this::inactive);
    }

    private AdministrationException inactive() {
        return new AdministrationException("config.active.absent", "There is no active configuration to edit.",
                "Complete setup or restore the last known-good configuration.");
    }

    private static AdministrationException unsupportedMoneyEditor() {
        return new AdministrationException("config.gui.money.unsupported",
                "The active Money configuration is not supported by this guided editor.",
                "Use the read-only view or advanced canonical configuration workflow; no data was simplified.");
    }

    private static AdministrationException unsupportedRewardEditor() {
        return new AdministrationException("config.gui.reward.unsupported",
                "The active Reward configuration is not supported by this guided editor.",
                "Use the read-only view or advanced canonical configuration workflow; no data was simplified.");
    }

    private static AdministrationException unsupportedTotalSkillLevelEditor() {
        return new AdministrationException("config.gui.total_skill_level.unsupported",
                "The active Total Skill Level requirement is not supported by this bounded guided editor.",
                "Use the read-only view or YAML for complex requirement trees; no structure was simplified.");
    }

    private static AdministrationException unsupportedScalingEditor() {
        return new AdministrationException("config.gui.scaling.unsupported",
                "The active Scaling configuration is not supported by this bounded guided editor.",
                "Use the read-only view or YAML for advanced editing; no scaling structure was simplified.");
    }

    private static AdministrationException invalidScalingValue(GuidedScalingParameter parameter) {
        return new AdministrationException("config.gui.scaling.invalid",
                "The " + scalingParameterName(parameter) + " is outside its canonical exact-decimal domain.",
                "Enter a non-negative plain decimal within the canonical scaling bounds.");
    }

    private static AdministrationException invalidScalingOverride() {
        return new AdministrationException("config.gui.scaling.override.invalid",
                "The Prestige Override is outside its canonical exact-decimal domain.",
                "Enter a non-negative plain decimal within the canonical scaling bounds.");
    }

    private static String scalingParameterName(GuidedScalingParameter parameter) {
        return switch (Objects.requireNonNull(parameter, "scaling parameter")) {
            case LINEAR_BASE -> "Linear base";
            case LINEAR_INCREMENT -> "Linear increment";
        };
    }

    private static AdministrationException stale() {
        return new AdministrationException("config.revision.stale",
                "The active configuration changed while this edit was open.",
                "Reopen Configuration and review the current values before editing again.");
    }

    private synchronized MoneyReviewAuthority consumeMoneyReview(PermissionSubject subject, UUID reviewId) {
        Objects.requireNonNull(reviewId, "review ID");
        MoneyReviewAuthority authority = moneyReviews.get(reviewId);
        if (authority == null || !Instant.now(clock).isBefore(authority.review().expiresAt())) {
            moneyReviews.remove(reviewId);
            throw new AdministrationException("config.gui.review.expired",
                    "This configuration review is absent or expired.",
                    "Prepare and review the Money change again.");
        }
        if (!authority.actor().equals(subject.actor())) {
            throw new AdministrationException("config.gui.review.actor_mismatch",
                    "A configuration review cannot be used by another staff member.",
                    "Open a separate Staff GUI session.");
        }
        if (!moneyReviews.remove(reviewId, authority)) {
            throw new AdministrationException("config.gui.review.replayed",
                    "This configuration review was already consumed.",
                    "Prepare a fresh Money change from current configuration.");
        }
        return authority;
    }

    private synchronized RewardReviewAuthority consumeRewardReview(PermissionSubject subject, UUID reviewId) {
        Objects.requireNonNull(reviewId, "review ID");
        RewardReviewAuthority authority = rewardReviews.get(reviewId);
        if (authority == null || !Instant.now(clock).isBefore(authority.review().expiresAt())) {
            rewardReviews.remove(reviewId);
            throw new AdministrationException("config.gui.review.expired",
                    "This configuration review is absent or expired.",
                    "Prepare and review the Reward change again.");
        }
        if (!authority.actor().equals(subject.actor())) {
            throw new AdministrationException("config.gui.review.actor_mismatch",
                    "A configuration review cannot be used by another staff member.",
                    "Open a separate Staff GUI session.");
        }
        if (!rewardReviews.remove(reviewId, authority)) {
            throw new AdministrationException("config.gui.review.replayed",
                    "This configuration review was already consumed.",
                    "Prepare a fresh Reward change from current configuration.");
        }
        return authority;
    }

    private synchronized TotalSkillLevelReviewAuthority consumeTotalSkillLevelReview(
            PermissionSubject subject,
            UUID reviewId) {
        Objects.requireNonNull(reviewId, "review ID");
        TotalSkillLevelReviewAuthority authority = totalSkillLevelReviews.get(reviewId);
        if (authority == null || !Instant.now(clock).isBefore(authority.review().expiresAt())) {
            totalSkillLevelReviews.remove(reviewId);
            throw new AdministrationException("config.gui.review.expired",
                    "This configuration review is absent or expired.",
                    "Prepare and review the Total Skill Level change again.");
        }
        if (!authority.actor().equals(subject.actor())) {
            throw new AdministrationException("config.gui.review.actor_mismatch",
                    "A configuration review cannot be used by another staff member.",
                    "Open a separate Staff GUI session.");
        }
        if (!totalSkillLevelReviews.remove(reviewId, authority)) {
            throw new AdministrationException("config.gui.review.replayed",
                    "This configuration review was already consumed.",
                    "Prepare a fresh Total Skill Level change from current configuration.");
        }
        return authority;
    }

    private synchronized ScalingReviewAuthority consumeScalingReview(PermissionSubject subject, UUID reviewId) {
        Objects.requireNonNull(reviewId, "review ID");
        ScalingReviewAuthority authority = scalingReviews.get(reviewId);
        if (authority == null || !Instant.now(clock).isBefore(authority.review().expiresAt())) {
            scalingReviews.remove(reviewId);
            throw new AdministrationException("config.gui.review.expired",
                    "This configuration review is absent or expired.",
                    "Prepare and review the Scaling change again.");
        }
        if (!authority.actor().equals(subject.actor())) {
            throw new AdministrationException("config.gui.review.actor_mismatch",
                    "A configuration review cannot be used by another staff member.",
                    "Open a separate Staff GUI session.");
        }
        if (!scalingReviews.remove(reviewId, authority)) {
            throw new AdministrationException("config.gui.review.replayed",
                    "This configuration review was already consumed.",
                    "Prepare a fresh Scaling change from current configuration.");
        }
        return authority;
    }

    private synchronized ScalingOverrideReviewAuthority consumeScalingOverrideReview(
            PermissionSubject subject,
            UUID reviewId) {
        Objects.requireNonNull(reviewId, "review ID");
        ScalingOverrideReviewAuthority authority = scalingOverrideReviews.get(reviewId);
        if (authority == null || !Instant.now(clock).isBefore(authority.review().expiresAt())) {
            scalingOverrideReviews.remove(reviewId);
            throw new AdministrationException("config.gui.review.expired",
                    "This configuration review is absent or expired.",
                    "Prepare and review the Override change again.");
        }
        if (!authority.actor().equals(subject.actor())) {
            throw new AdministrationException("config.gui.review.actor_mismatch",
                    "A configuration review cannot be used by another staff member.",
                    "Open a separate Staff GUI session.");
        }
        if (!scalingOverrideReviews.remove(reviewId, authority)) {
            throw new AdministrationException("config.gui.review.replayed",
                    "This configuration review was already consumed.",
                    "Prepare a fresh Override change from current configuration.");
        }
        return authority;
    }

    private void pruneReviews() {
        Instant now = Instant.now(clock);
        moneyReviews.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().review().expiresAt()));
        rewardReviews.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().review().expiresAt()));
        totalSkillLevelReviews.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().review().expiresAt()));
        scalingReviews.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().review().expiresAt()));
        scalingOverrideReviews.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().review().expiresAt()));
    }

    private record MoneyBinding(
            RequirementDefinition requirement,
            CostDefinition cost,
            String requirementAmount,
            String costAmount,
            String requirementBase,
            int requirementSegment,
            String scalingDescription,
            boolean editable,
            GuidedMoneyScalingPair pair) {
        private String currentDisplay() {
            return requirementAmount.equals(costAmount)
                    ? requirementAmount : requirementAmount + " requirement / " + costAmount + " cost";
        }
    }

    private record RewardBinding(RewardDefinition reward, String amount, boolean editable) {
    }

    private record MoneyInputValidation(
            ActivePhaseFourConfiguration snapshot,
            MoneyBinding binding,
            String normalizedAmount,
            String multiplier) {
    }

    private record RewardInputValidation(
            ActivePhaseFourConfiguration snapshot,
            RewardBinding binding,
            String normalizedAmount) {
    }

    private record TotalSkillLevelBinding(RequirementDefinition requirement, String currentTarget) {
    }

    private record TotalSkillLevelInputValidation(
            ActivePhaseFourConfiguration snapshot,
            TotalSkillLevelBinding binding,
            String normalizedTarget) {
    }

    private record ScalingBinding(
            RequirementDefinition requirement,
            int segmentIndex,
            GuidedScalingConfigurationView view,
            GuidedMoneyScalingPair pair) {
    }

    private record ScalingInputValidation(
            ActivePhaseFourConfiguration snapshot,
            ScalingBinding binding,
            String normalizedValue) {
    }

    private record ScalingOverrideInputValidation(
            ActivePhaseFourConfiguration snapshot,
            ScalingBinding binding,
            String normalizedValue) {
    }

    private record MoneyReviewAuthority(
            Actor actor,
            UUID draftId,
            Optional<UUID> acknowledgementId,
            GuidedMoneyConfigurationReview review) {
        private MoneyReviewAuthority {
            actor = Objects.requireNonNull(actor, "actor");
            draftId = Objects.requireNonNull(draftId, "draft ID");
            acknowledgementId = Objects.requireNonNull(acknowledgementId, "acknowledgement ID");
            review = Objects.requireNonNull(review, "review");
        }
    }

    private record RewardReviewAuthority(
            Actor actor,
            UUID draftId,
            Optional<UUID> acknowledgementId,
            GuidedRewardConfigurationReview review) {
        private RewardReviewAuthority {
            actor = Objects.requireNonNull(actor, "actor");
            draftId = Objects.requireNonNull(draftId, "draft ID");
            acknowledgementId = Objects.requireNonNull(acknowledgementId, "acknowledgement ID");
            review = Objects.requireNonNull(review, "review");
        }
    }

    private record TotalSkillLevelReviewAuthority(
            Actor actor,
            UUID draftId,
            Optional<UUID> acknowledgementId,
            GuidedTotalSkillLevelReview review) {
        private TotalSkillLevelReviewAuthority {
            actor = Objects.requireNonNull(actor, "actor");
            draftId = Objects.requireNonNull(draftId, "draft ID");
            acknowledgementId = Objects.requireNonNull(acknowledgementId, "acknowledgement ID");
            review = Objects.requireNonNull(review, "review");
        }
    }

    private record ScalingReviewAuthority(
            Actor actor,
            UUID draftId,
            Optional<UUID> acknowledgementId,
            GuidedScalingConfigurationReview review) {
        private ScalingReviewAuthority {
            actor = Objects.requireNonNull(actor, "actor");
            draftId = Objects.requireNonNull(draftId, "draft ID");
            acknowledgementId = Objects.requireNonNull(acknowledgementId, "acknowledgement ID");
            review = Objects.requireNonNull(review, "review");
        }
    }

    private record ScalingOverrideReviewAuthority(
            Actor actor,
            UUID draftId,
            Optional<UUID> acknowledgementId,
            GuidedScalingOverrideReview review) {
        private ScalingOverrideReviewAuthority {
            actor = Objects.requireNonNull(actor, "actor");
            draftId = Objects.requireNonNull(draftId, "draft ID");
            acknowledgementId = Objects.requireNonNull(acknowledgementId, "acknowledgement ID");
            review = Objects.requireNonNull(review, "review");
        }
    }
}
