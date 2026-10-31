package `in`.mrgb.anton.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.mrgb.anton.data.api.ModelDto
import `in`.mrgb.anton.ui.components.*
import `in`.mrgb.anton.ui.theme.Anton
import `in`.mrgb.anton.ui.theme.Logos
import `in`.mrgb.anton.ui.theme.Lucide
import `in`.mrgb.anton.ui.theme.Type

/**
 * The web's model catalog helpers (src/web/lib/model-catalog.ts): where a
 * model's calls go, the filters and sorts, the reasoning scale, and a plan
 * model's context and fast variants.
 */
object Catalog {
    private val GATEWAYS = mapOf("openrouter" to "OpenRouter", "vercel" to "Vercel AI Gateway")

    /** The plan you signed in to, such as "ChatGPT", or the gateway that lists it. */
    fun sourceOf(m: ModelDto): String = m.subscription ?: m.id.substringBefore('/').let { GATEWAYS[it] ?: it }

    data class Source(val name: String, val plan: Boolean, val count: Int)

    /** Plans first, then the gateways, each with how many models it offers. */
    fun sourcesOf(models: List<ModelDto>): List<Source> {
        val counts = models.groupingBy(::sourceOf).eachCount()
        val plans = models.filter { it.subscription != null }.map(::sourceOf).distinct()
        return plans.map { Source(it, true, counts[it] ?: 0) } + counts.filterKeys { it !in plans }.map { (n, c) -> Source(n, false, c) }
    }

    enum class Filter(val label: String, val icon: ImageVector?, val test: (ModelDto) -> Boolean) {
        Reasoning("Reasoning", Lucide.Brain, { it.reasoning.isNotEmpty() }),
        Vision("Vision", Lucide.Eye, { it.vision }),
        Free("Free", null, { it.subscription == null && it.price.input == 0.0 && it.price.output == 0.0 }),
        Long("256K+", null, { it.contextLength >= 256_000 }),
    }

    enum class Sort(val label: String, val compare: Comparator<ModelDto>) {
        Newest("Newest", compareByDescending { it.createdAt }),
        Cheapest("Cheapest", compareBy<ModelDto> { it.price.output }.thenBy { it.price.input }),
        Context("Context", compareByDescending { it.contextLength }),
    }

    /** Every word of the query must appear in the model's name, vendor or id. */
    fun matches(m: ModelDto, q: String): Boolean {
        val hay = "${m.vendor.orEmpty()} ${m.name} ${m.id}".lowercase()
        return q.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }.all { it in hay }
    }

    val LEVEL_LABEL = mapOf("off" to "Off", "minimal" to "Min", "low" to "Low", "medium" to "Med", "high" to "High", "xhigh" to "X-High", "max" to "Max")

    /** The level a task runs at: its choice when the model takes it, else the model's default. */
    fun effective(m: ModelDto?, chosen: String?): String {
        if (m == null || m.reasoning.isEmpty()) return "off"
        return if (chosen != null && chosen in m.reasoning) chosen else m.defaultReasoning
    }

    /** "Anthropic Claude Sonnet 4.5" by Anthropic reads as "Claude Sonnet 4.5". */
    fun shortName(m: ModelDto): String {
        val v = m.vendor.orEmpty()
        val t = if (v.isNotBlank() && m.name.startsWith("$v ", ignoreCase = true)) m.name.substring(v.length + 1) else m.name
        return t.ifBlank { m.name.ifBlank { m.id.substringAfterLast('/') } }
    }

    fun tokens(n: Long): String = when {
        n >= 1_000_000 -> "%.1fM".format(n / 1_048_576.0).replace(".0M", "M")
        n >= 1000 -> "${Math.round(n / 1000.0)}K"
        else -> "$n"
    }

    fun dollars(perMillion: Double): String = when {
        perMillion == 0.0 -> "$0"
        perMillion < 1 -> "$" + "%.3f".format(perMillion).trimEnd('0').trimEnd('.')
        else -> "$" + "%.2f".format(perMillion).trimEnd('0').trimEnd('.')
    }

    private val HUES = listOf(0xFFB98BE8, 0xFF8FC98D, 0xFFE0AC63, 0xFFDCC98A, 0xFFE58FA8, 0xFF6FCDE8, 0xFFF2C173, 0xFF9EE1F3)

    /** A stable hue per vendor, so a vendor's models read as one family. */
    fun vendorHue(vendor: String): Color {
        var hash = 0L
        for (c in vendor) hash = (hash * 31 + c.code) and 0xFFFFFFFFL
        return Color(HUES[(hash % HUES.size).toInt()])
    }

    /** A model and the variants its vendor offers: contexts (null for the standard one), a fast mode, and the model for any choice. */
    class Options(val base: ModelDto, val contexts: List<Pair<String?, Long>>, val fast: Boolean, val chosenContext: String?, val chosenFast: Boolean, private val family: List<ModelDto>) {
        fun pick(context: String?, fast: Boolean): ModelDto =
            family.firstOrNull { (it.variant?.context) == context && (it.variant?.fast ?: false) == fast }
                ?: family.firstOrNull { (it.variant?.context) == context && it.variant?.fast != true }
                ?: base
    }

    fun optionsOf(models: List<ModelDto>, model: ModelDto): Options {
        val base = model.variant?.let { v -> models.firstOrNull { it.id == v.of } } ?: model
        val family = listOf(base) + models.filter { it.variant?.of == base.id }
        val contexts = linkedMapOf<String?, Long>(null to base.contextLength)
        family.forEach { m -> m.variant?.context?.let { contexts[it] = m.contextLength } }
        return Options(
            base = base,
            contexts = contexts.entries.map { it.key to it.value }.sortedBy { it.second },
            fast = family.any { it.variant?.fast == true },
            chosenContext = if (base === model) null else model.variant?.context,
            chosenFast = if (base === model) false else model.variant?.fast == true,
            family = family,
        )
    }

    /** A model by id, also when the id carries variant tags the catalog doesn't list, like `gpt-6.1-terra[872k][fast]`. */
    fun find(models: List<ModelDto>, id: String): ModelDto? {
        val bare = id.replace(Regex("(\\[[^\\]]*\\])+$"), "")
        return models.firstOrNull { it.id == id } ?: models.firstOrNull { it.id == bare }
            ?: models.firstOrNull { it.id.substringAfterLast('/') == bare.substringAfterLast('/') && it.variant == null }
    }

    /** "gpt-6.1-terra" → "GPT-6.1 Terra", for a model the catalog doesn't know. */
    fun prettyId(id: String): String {
        val words = id.substringAfterLast('/').replace(Regex("(\\[[^\\]]*\\])+$"), "").split('-').filter { it.isNotBlank() }
        val out = mutableListOf<String>()
        for (w in words) {
            val first = w.first()
            when {
                first.isDigit() && out.isNotEmpty() && out.last().all { it.isUpperCase() || it.isDigit() } -> out[out.lastIndex] = out.last() + "-" + w
                w.lowercase() in setOf("gpt", "glm", "o1", "o3", "o4") -> out += w.uppercase()
                else -> out += w.replaceFirstChar { it.uppercase() }
            }
        }
        return out.joinToString(" ").ifBlank { id }
    }

    /** What the composer chip says: the model's name only, like "GPT-6.1 Terra". */
    fun chipLabel(models: List<ModelDto>, id: String): String =
        find(models, id)?.let { m -> shortName(optionsOf(models, m).base) } ?: prettyId(id)
}

/**
 * The model picker as a sheet: where the calls go (All, each plan, each
 * gateway), the web's filters (Reasoning, Vision, Free, 256K+) and sort, the
 * catalog grouped by vendor with a plan model's variants folded into it, and
 * an options panel for the chosen model: effort, context and fast mode.
 */
@Composable
fun ModelPickerSheet(
    models: List<ModelDto>,
    selected: String,
    reasoning: String? = null,
    onPick: (model: String, reasoning: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    AntonSheet(onDismiss = onDismiss, title = "Model", subtitle = if (models.isEmpty()) "Loading models…" else null) {
        ModelPicker(models, selected, reasoning, onPick, Modifier.fillMaxHeight(0.88f))
    }
}

/** The picker's body, outside its sheet so screenshots can render it. */
@Composable
fun ModelPicker(
    models: List<ModelDto>,
    selected: String,
    reasoning: String?,
    onPick: (model: String, reasoning: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var q by remember { mutableStateOf("") }
    var source by remember { mutableStateOf<String?>(null) }
    var filters by remember { mutableStateOf(emptySet<Catalog.Filter>()) }
    var sort by remember { mutableStateOf(Catalog.Sort.Newest) }
    val current = models.firstOrNull { it.id == selected } ?: models.firstOrNull { it.id.substringAfterLast('/') == selected }
    val currentOpts = current?.let { Catalog.optionsOf(models, it) }
    var staged by remember(models.size) { mutableStateOf(currentOpts?.base) }
    val opts = staged?.let { Catalog.optionsOf(models, it) }
    val isCurrent = staged != null && staged?.id == currentOpts?.base?.id
    var ctx by remember(staged) { mutableStateOf(if (isCurrent) currentOpts?.chosenContext else null) }
    var fast by remember(staged) { mutableStateOf(isCurrent && currentOpts?.chosenFast == true) }
    var effort by remember(staged) { mutableStateOf(staged?.let { if (isCurrent) Catalog.effective(it, reasoning) else it.defaultReasoning }) }

    val listed = models.filter { it.variant == null }
    val sources = Catalog.sourcesOf(listed)
    val shown = listed.filter { m ->
        (source == null || Catalog.sourceOf(m) == source) && Catalog.matches(m, q) && filters.all { it.test(m) }
    }.sortedWith(sort.compare)

    Column(modifier.fillMaxWidth()) {
        AntonInput(
            q, { q = it },
            placeholder = "Search models…",
            leadingIcon = Lucide.Search,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            trailing = if (q.isNotEmpty()) ({
                Icon(Lucide.X, "Clear", tint = Anton.IconTertiary, modifier = Modifier.size(22.dp).clip(RoundedCornerShape(5.dp)).clickable { q = "" }.padding(4.dp))
            }) else null,
        )
        // Where the calls go: every source, each plan you pay for, each gateway.
        if (sources.size > 1) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Pill("All", listed.size, on = source == null) { source = null }
                sources.forEach { s ->
                    Pill(s.name, s.count, on = source == s.name, leading = { SourceMark(s.name, s.plan, 14.dp) }) {
                        source = if (source == s.name) null else s.name
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 10.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Lucide.ListFilter, null, tint = Anton.IconTertiary, modifier = Modifier.size(14.dp))
            Catalog.Filter.entries.forEach { f ->
                Pill(f.label, null, on = f in filters, icon = f.icon, check = true) { filters = if (f in filters) filters - f else filters + f }
            }
            Box(Modifier.width(1.dp).height(16.dp).background(Anton.BorderDefault))
            Pill(sort.label, null, on = false, icon = Lucide.ArrowUpDown) {
                sort = Catalog.Sort.entries[(sort.ordinal + 1) % Catalog.Sort.entries.size]
            }
        }
        LazyColumn(Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 12.dp)) {
            // Vendors in the order the sort puts their best model.
            shown.groupBy { it.vendor ?: it.provider ?: "Other" }.forEach { (vendor, group) ->
                item(key = "v-$vendor") {
                    Row(Modifier.padding(start = 12.dp, end = 12.dp, top = 18.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        group.first().let { ModelLogo(it.id, it.vendor, 12.dp) }
                        SectionLabel(vendor, group.size, Modifier)
                    }
                }
                items(group, key = { it.id }) { m ->
                    ModelRow(m, on = m.id == staged?.id, current = currentOpts?.base?.id == m.id) { staged = m }
                }
            }
            if (shown.isEmpty() && models.isNotEmpty()) item {
                EmptyState(Lucide.Search, "No models match", if (filters.isNotEmpty() || source != null) "Clear a filter or pick another source." else "Try a vendor or a model id.")
            }
        }
        staged?.let { s ->
            OptionsPanel(
                model = s, opts = opts!!, effort = effort, onEffort = { effort = it },
                context = ctx, onContext = { ctx = it }, fast = fast, onFast = { fast = it },
                onUse = {
                    val chosen = opts.pick(ctx, fast)
                    onPick(chosen.id, if (s.reasoning.isEmpty()) null else effort)
                },
            )
        }
    }
}

/**
 * The model's vendor logo (the web's LobeHub set); a one-colour logo takes
 * the text colour. A vendor without one gets its initial on its hue.
 */
@Composable
fun ModelLogo(id: String, vendor: String?, size: androidx.compose.ui.unit.Dp = 14.dp) {
    val logo = remember(id, vendor) { Logos.forModel(id, vendor) }
    when {
        logo == null -> InitialTile(vendor?.ifBlank { null } ?: Logos.slugOf(id), size)
        logo.mono -> Icon(androidx.compose.ui.res.painterResource(logo.res), vendor, tint = Anton.TextPrimary, modifier = Modifier.size(size))
        else -> androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(logo.res), vendor, Modifier.size(size))
    }
}

/** The accent dot that marks a model on a plan you pay for, on its mark's corner. */
@Composable
fun PlanDot(modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 6.dp) {
    Box(modifier.size(size + 3.dp).clip(CircleShape).background(Anton.Raised), contentAlignment = Alignment.Center) {
        Box(Modifier.size(size).clip(CircleShape).background(Anton.Accent))
    }
}

/** A source's logo (a plan's or a gateway's), its initial when the set has none; a plan's carries the dot. */
@Composable
fun SourceMark(name: String, plan: Boolean, size: androidx.compose.ui.unit.Dp = 14.dp) {
    val logo = remember(name) { Logos.forSource(name) }
    Box {
        when {
            logo == null -> InitialTile(name, size)
            logo.mono -> Icon(androidx.compose.ui.res.painterResource(logo.res), name, tint = Anton.TextPrimary, modifier = Modifier.size(size))
            else -> androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(logo.res), name, Modifier.size(size))
        }
        if (plan) PlanDot(Modifier.align(Alignment.TopEnd).offset(x = 3.dp, y = (-3).dp), size = (size.value * 0.36f).coerceAtLeast(4f).dp)
    }
}

/**
 * What the composer chip leads with: where the model's calls go. A plan model
 * shows its plan's logo (ChatGPT, Claude, Cursor…) with the accent dot; any
 * other shows its vendor's logo.
 */
@Composable
fun ModelMark(model: ModelDto?, id: String, size: androidx.compose.ui.unit.Dp = 14.dp) {
    val plan = model?.subscription
    if (plan != null && Logos.forSource(plan) != null) SourceMark(plan, plan = true, size = size)
    else Box {
        ModelLogo(model?.id ?: id, model?.vendor, size)
        if (plan != null) PlanDot(Modifier.align(Alignment.TopEnd).offset(x = 3.dp, y = (-3).dp), size = (size.value * 0.36f).coerceAtLeast(4f).dp)
    }
}

/**
 * The chosen model's settings, small enough to sit in the chip: effort as a
 * four-step meter, the context when the model comes in more than one, and a
 * bolt for fast mode.
 */
@Composable
fun ModelFacts(models: List<ModelDto>, id: String, reasoning: String?) {
    val m = Catalog.find(models, id) ?: return
    val opts = Catalog.optionsOf(models, m)
    val base = opts.base
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        if (base.reasoning.isNotEmpty()) {
            val level = Catalog.effective(base, reasoning)
            val steps = base.reasoning.filter { it != "off" }
            val strength = if (level == "off" || steps.isEmpty()) 0f else (steps.indexOf(level) + 1f) / steps.size
            val lit = kotlin.math.ceil(strength * 4).toInt()
            Row(Modifier.height(10.dp), horizontalArrangement = Arrangement.spacedBy(1.5.dp), verticalAlignment = Alignment.Bottom) {
                for (i in 0 until 4) {
                    Box(Modifier.width(2.5.dp).height((4 + i * 2).dp).clip(RoundedCornerShape(1.dp)).background(if (i < lit) Anton.Accent else Anton.N600))
                }
            }
        }
        if (opts.contexts.size > 1) {
            val tag = m.variant?.context
            val tokens = opts.contexts.firstOrNull { it.first == tag }?.second ?: base.contextLength
            Text(Catalog.tokens(tokens), style = Type.Num.copy(fontSize = 10.5.sp), color = Anton.TextTertiary, maxLines = 1)
        }
        if (m.variant?.fast == true) Icon(Lucide.Zap, "Fast", tint = Anton.Warning, modifier = Modifier.size(11.dp))
    }
}

@Composable
private fun InitialTile(name: String, size: androidx.compose.ui.unit.Dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.28f)).background(Catalog.vendorHue(name).copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
        Text(name.take(1).uppercase(), style = Type.SmallMedium.copy(fontSize = (size.value * 0.6f).sp, lineHeight = (size.value * 0.7f).sp), color = Catalog.vendorHue(name), maxLines = 1)
    }
}

@Composable
private fun Pill(label: String, count: Int?, on: Boolean, icon: ImageVector? = null, check: Boolean = false, leading: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        Modifier.height(30.dp).clip(shape)
            .background(if (on) Anton.AccentBg else Anton.InputBg)
            .border(1.dp, if (on) Anton.AccentBorder else Anton.Input, shape)
            .clickable(role = Role.Checkbox, onClick = onClick)
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val tint = if (on) Anton.AccentHover else Anton.IconTertiary
        leading?.invoke()
        if (check && on) Icon(Lucide.Check, null, tint = tint, modifier = Modifier.size(12.dp))
        else icon?.let { Icon(it, null, tint = tint, modifier = Modifier.size(12.dp)) }
        Text(label, style = Type.SmallMedium, color = if (on) Anton.AccentHover else Anton.TextSecondary, maxLines = 1)
        count?.let { Text("$it", style = Type.Num.copy(fontSize = 10.5.sp), color = if (on) Anton.Accent else Anton.TextDisabled) }
    }
}

@Composable
private fun ModelRow(m: ModelDto, on: Boolean, current: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .background(if (on) Anton.AccentBg else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val tile = RoundedCornerShape(8.dp)
        Box(Modifier.size(32.dp).clip(tile).background(Anton.Overlay).border(1.dp, Anton.BorderSubtle, tile), contentAlignment = Alignment.Center) {
            ModelLogo(m.id, m.vendor, 16.dp)
            if (m.subscription != null) PlanDot(Modifier.align(Alignment.TopEnd).offset(x = 2.dp, y = (-2).dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(Catalog.shortName(m), style = Type.BodyMedium, color = if (on) Anton.AccentHover else Anton.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (m.reasoning.isNotEmpty()) Icon(Lucide.Brain, "Reasons", tint = Anton.IconDisabled, modifier = Modifier.size(12.dp))
                if (m.vision) Icon(Lucide.Eye, "Sees images", tint = Anton.IconDisabled, modifier = Modifier.size(12.dp))
                if (current) Text("current", style = Type.Meta.copy(fontSize = 10.5.sp), color = Anton.TextDisabled)
            }
            val price = if (m.subscription != null) m.subscription else if (m.price.input == 0.0 && m.price.output == 0.0) "Free" else "${Catalog.dollars(m.price.input)} / ${Catalog.dollars(m.price.output)}"
            Text(
                listOfNotNull(Catalog.sourceOf(m).takeIf { m.subscription == null }, price).joinToString(" · "),
                style = Type.Num, color = Anton.TextDisabled, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (m.contextLength > 0) Text(Catalog.tokens(m.contextLength), style = Type.Num, color = Anton.TextTertiary)
        Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) {
            if (on) Icon(Lucide.Check, "Chosen", tint = Anton.Accent, modifier = Modifier.size(15.dp))
        }
    }
}

/** The chosen model's controls — effort, context and fast mode — and the button that uses it. */
@Composable
private fun OptionsPanel(
    model: ModelDto, opts: Catalog.Options,
    effort: String?, onEffort: (String) -> Unit,
    context: String?, onContext: (String?) -> Unit,
    fast: Boolean, onFast: (Boolean) -> Unit,
    onUse: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().hairlineTop(Anton.BorderDefault)
            .background(Brush.verticalGradient(listOf(Anton.Overlay, Anton.Raised)))
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ModelMark(model, model.id, 18.dp)
            Text(Catalog.shortName(model), style = Type.BodyMedium, color = Anton.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Badge(Catalog.sourceOf(model), if (model.subscription != null) Tone.Accent else Tone.Neutral)
        }
        if (model.reasoning.isNotEmpty()) {
            OptionRow("Effort", Lucide.Gauge) {
                Segments(model.reasoning.map { it to (Catalog.LEVEL_LABEL[it] ?: it) }, effort ?: model.defaultReasoning, onEffort, default = model.defaultReasoning)
            }
        }
        if (opts.contexts.size > 1) {
            OptionRow("Context", Lucide.Braces) {
                Segments(opts.contexts.map { (tag, n) -> tag to Catalog.tokens(n) }, context, onContext)
            }
        }
        if (opts.fast) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Lucide.Zap, null, tint = Anton.IconTertiary, modifier = Modifier.size(13.dp))
                Column(Modifier.weight(1f)) {
                    Text("Fast mode", style = Type.SmallMedium, color = Anton.TextPrimary)
                    Text("Quicker answers on the same model, billed higher", style = Type.Meta, color = Anton.TextTertiary)
                }
                AntonSwitch(fast, onFast)
            }
        }
        AntonButton("Use ${Catalog.shortName(model)}", onClick = onUse, modifier = Modifier.fillMaxWidth(), size = ButtonSize.Lg)
    }
}

@Composable
private fun OptionRow(label: String, icon: ImageVector, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, tint = Anton.IconTertiary, modifier = Modifier.size(12.dp))
            Text(label.uppercase(), style = Type.MonoLabel, color = Anton.TextTertiary)
        }
        content()
    }
}

/** A full-width segmented control: each option takes an equal share; the model's default carries a dot. */
@Composable
private fun <T> Segments(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, default: T? = null, markDefault: Boolean = default != null) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).border(1.dp, Anton.BorderSubtle, RoundedCornerShape(10.dp))
            .background(Anton.WellBg).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEach { (value, label) ->
            val on = value == selected
            val shape = RoundedCornerShape(7.dp)
            Box(
                Modifier.weight(1f).height(32.dp).clip(shape)
                    .then(if (on) Modifier.background(Brush.verticalGradient(listOf(Anton.N700, Anton.N750))).border(1.dp, Anton.BorderDefault, shape) else Modifier)
                    .clickable(role = Role.Tab) { onSelect(value) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = Type.SmallMedium.copy(fontSize = 11.5.sp), color = if (on) Anton.TextPrimary else Anton.TextTertiary, maxLines = 1)
                if (markDefault && value == default) Box(Modifier.align(Alignment.TopEnd).padding(top = 4.dp, end = 4.dp).size(3.dp).clip(CircleShape).background(if (on) Anton.Accent else Anton.N500))
            }
        }
    }
}
