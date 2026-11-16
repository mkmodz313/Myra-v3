package com.myra.assistant.keyboard

import android.content.Context

/** Word suggestions shown above the keys: built-in common words + words the user types (learned). */
class KbSuggest(private val ctx: Context) {

    private val learned = LinkedHashMap<String, Int>()
    private var dirty = 0

    init {
        try {
            val raw = ctx.getSharedPreferences("myra_kb_words", Context.MODE_PRIVATE).getString("w", "") ?: ""
            for (line in raw.split("\n")) {
                val i = line.lastIndexOf('\t')
                if (i > 0) {
                    val n = line.substring(i + 1).toIntOrNull() ?: continue
                    learned[line.substring(0, i)] = n
                }
            }
        } catch (e: Throwable) {
        }
    }

    fun learn(word: String) {
        if (word.length < 2 || word.any { it.isDigit() }) return
        val w = word.trim('\'')
        if (w.length < 2) return
        val key = if (w.length > 1 && w.substring(1).all { it.isLowerCase() }) w.lowercase() else w
        learned[key] = (learned[key] ?: 0) + 1
        dirty++
        if (dirty >= 8) save()
    }

    fun save() {
        dirty = 0
        try {
            val top = learned.entries.sortedByDescending { it.value }.take(3000)
            val sb = StringBuilder()
            for (e in top) sb.append(e.key).append('\t').append(e.value).append('\n')
            ctx.getSharedPreferences("myra_kb_words", Context.MODE_PRIVATE).edit().putString("w", sb.toString()).apply()
        } catch (e: Throwable) {
        }
    }

    private fun matchCase(prefix: String, w: String): String {
        if (prefix.length > 1 && prefix.all { it.isUpperCase() }) return w.uppercase()
        if (prefix.isNotEmpty() && prefix[0].isUpperCase()) return w.replaceFirstChar { it.uppercaseChar() }
        return w
    }

    /** Up to 3 suggestions for the word being typed. Empty prefix = most used words. */
    fun suggest(prefix: String): List<String> {
        val out = ArrayList<String>()
        val seen = HashSet<String>()
        val lp = prefix.lowercase()
        if (lp.isEmpty()) {
            for (e in learned.entries.sortedByDescending { it.value }) {
                if (out.size >= 3) break
                if (seen.add(e.key.lowercase())) out.add(e.key)
            }
            for (w in START) {
                if (out.size >= 3) break
                if (seen.add(w)) out.add(w)
            }
            return out
        }
        for (e in learned.entries.sortedByDescending { it.value }) {
            if (out.size >= 3) break
            val k = e.key
            if (k.lowercase().startsWith(lp) && k.lowercase() != lp && seen.add(k.lowercase())) out.add(matchCase(prefix, k))
        }
        if (out.size < 3) {
            for (w in WORDS) {
                if (out.size >= 3) break
                if (w.startsWith(lp) && w != lp && seen.add(w)) out.add(matchCase(prefix, w))
            }
        }
        return out
    }

    companion object {
        private val START = listOf("I", "The", "Hi")

        /** Roughly most-common first (English + Roman Hindi / Urdu + Islamic words). */
        val WORDS: List<String> = (
            "the be to of and a in that have i it for not on with he as you do at this but his by from they we say her she or an will my one all would there their what so up out if about who get which go me when make can like time no just him know take people into year your good some could them see other than then now look only come its over think also back after use two how our work first well way even new want because any these give day most us " +
            "is are was were been being has had did does done am hello hi hey thanks thank please sorry okay ok yes yeah welcome morning afternoon evening night today tomorrow yesterday tonight week month always never sometimes maybe really very much many more less little big small long short high low old young great best better worst bad happy sad angry love like hate friend family brother sister mother father mom dad son daughter wife husband baby man woman boy girl child children kid " +
            "where when why which while whose whom whatever whenever wherever however although though unless until since before between among against during without within through across behind beside above below under around near far inside outside forward toward away again already still yet soon later early late ever once twice another each every both either neither few several enough such same different next last own " +
            "home house room door window car bike bus train road street city town village country world water food eat drink tea coffee milk bread rice meat chicken fish fruit apple banana egg lunch dinner breakfast hungry thirsty sleep wake dream rest tired busy free ready wait stop start finish begin end open close open " +
            "call phone message text mail email send receive read write reply answer ask tell talk speak listen hear watch look see show find search help need want try learn teach study school college class exam test result marks book pen paper note page word line number name address city " +
            "work job office boss team meeting project plan idea business money pay price cost cheap costly buy sell shop market order delivery payment account bank card cash online offline internet wifi network data mobile app apps video photo picture image song music movie game play player match score win lose " +
            "think thought believe feel feeling mind heart soul life live living alive death dead die born grow healthy health doctor medicine hospital pain fever cold cough better recover " +
            "beautiful pretty nice cool awesome amazing wonderful perfect lovely cute sweet kind funny crazy strange weird serious true false right wrong correct mistake problem issue solution question answer reason because reply " +
            "people person someone anyone everyone nobody something anything everything nothing somewhere anywhere everywhere nowhere sometime anytime everytime " +
            "going coming doing being having making taking giving getting working playing reading writing talking walking running eating drinking sleeping waiting looking thinking trying using calling asking telling saying meeting " +
            "tomorrow monday tuesday wednesday thursday friday saturday sunday january february march april may june july august september october november december " +
            "because should would could might must shall cannot dont wont cant didnt doesnt isnt arent wasnt werent havent hasnt hadnt shouldnt wouldnt couldnt im ive ill id youre youve youll youd hes shes its were theyre theyve theyll thats whats whos wheres hows lets " +
            "congratulations birthday wishes blessed blessing prayer pray god allah mashallah inshallah alhamdulillah subhanallah astaghfirullah bismillah assalamualaikum walaikumassalam jazakallah barakallah ameen eid mubarak ramadan jummah salam dua " +
            "kya hai hain nahi nahin haan han ji acha accha theek thik bilkul bahut bohot zyada kam thoda abhi phir fir kal aaj parso kab kahan kaha kaise kaisa kaisi kyun kyu kisne kisko kisi koi kuch sab sabhi sirf bas lekin magar par aur ya toh to bhi hi hee main mai mein hum tum aap tu woh wo ye yeh yahan wahan idhar udhar upar neeche andar bahar saath sath bina liye liya kiya kiye karna karo karein kar raha rahi rahe tha thi the hoga hogi honge hua hui hue gaya gayi gaye aaya aayi aaye jana jao jaana aana aao milna milte mila mili mile dekho dekha dekhna suno suna sunna bolo bola bolna batao bata batana pata maloom samajh samjha samajhna chahiye chahta chahti chahte pasand pyaar mohabbat dost yaar bhai behen ammi abbu ammi papa mummy beta beti bhabhi chacha mama nana nani dada dadi " +
            "mera meri mere tera teri tere uska uski uske hamara hamari hamare tumhara tumhari tumhare aapka aapki aapke kaun kaunsa kitna kitni kitne kab tak se ko ka ki ke ne pe par mein ghar kaam paisa paise khana pani chai roti sabzi dawai doctor school college padhai exam result naukri shaadi mubarak shukriya dhanyavaad maaf maafi sorry please zaroor zaruri jaldi dheere dhire jaldi raat subah shaam dopahar din " +
            "welcome hello keyboard myra stylish font theme emoji symbol sticker clipboard settings design color colour size height width button key"
            ).split(" ").filter { it.isNotEmpty() }.distinct()
    }
}
