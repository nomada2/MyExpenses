package org.totschnig.myexpenses.activity

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.view.isVisible
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import kotlinx.android.synthetic.main.one_budget.*
import org.threeten.bp.LocalDate
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.model.Grouping
import org.totschnig.myexpenses.model.Money
import org.totschnig.myexpenses.provider.DatabaseConstants.KEY_ROWID
import org.totschnig.myexpenses.ui.SpinnerHelper
import org.totschnig.myexpenses.viewmodel.Account
import org.totschnig.myexpenses.viewmodel.BudgetEditViewModel
import org.totschnig.myexpenses.viewmodel.data.Budget
import org.totschnig.myexpenses.viewmodel.data.getLabelForBudgetType

class BudgetEdit : EditActivity(), AdapterView.OnItemSelectedListener, DatePicker.OnDateChangedListener {
    lateinit var viewModel: BudgetEditViewModel
    override fun getDiscardNewMessage() = R.string.dialog_confirm_discard_new_budget
    var pendingBudgetLoad = 0L
    var resumedP = false
    private var budget: Budget? = null
    private lateinit var typeSpinnerHelper: SpinnerHelper
    private lateinit var accountSpinnerHelper: SpinnerHelper

    override fun setupListeners() {
        Title.addTextChangedListener(this)
        Description.addTextChangedListener(this)
        Amount.addTextChangedListener(this)
        typeSpinnerHelper.setOnItemSelectedListener(this)
        accountSpinnerHelper.setOnItemSelectedListener(this)
        (budget?.start ?: LocalDate.now()).let {
            DurationFrom.initWith(it, this)
        }
        (budget?.end ?: LocalDate.now()).let {
            DurationTo.initWith(it, this)
        }
    }

    private val budgetId
        get() = intent.extras?.getLong(KEY_ROWID) ?: 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.one_budget)
        setupToolbar()
        viewModel = ViewModelProviders.of(this).get(BudgetEditViewModel::class.java)
        viewModel.accounts.observe(this, Observer {
            accountSpinnerHelper.adapter = AccountAdapter(this, it)
            linkInputWithLabel(accountSpinnerHelper.spinner, AccountsLabel)
        })
        viewModel.budget.observe(this, Observer { populateData(it) })
        mNewInstance = budgetId == 0L
        if (savedInstanceState == null) {
            pendingBudgetLoad = budgetId
            viewModel.loadData(pendingBudgetLoad)
        }
        viewModel.databaseResult.observe(this, Observer {
            if (it) finish() else {
                Toast.makeText(this, "Error while saving budget", Toast.LENGTH_LONG).show()
            }
        })
        typeSpinnerHelper = SpinnerHelper(Type).apply {
            adapter = GroupingAdapter(this@BudgetEdit)
            setSelection(Grouping.MONTH.ordinal)
        }
        accountSpinnerHelper = SpinnerHelper(Accounts)
        linkInputWithLabels()
    }

    private fun linkInputWithLabels() {
        linkInputWithLabel(Title, TitleLabel)
        linkInputWithLabel(Description, DescriptionLabel)
        linkInputWithLabel(Amount, AmountLabel)
        linkInputWithLabel(typeSpinnerHelper.spinner, TypeLabel)
        linkInputWithLabel(DurationFrom, DurationFromLabel)
        linkInputWithLabel(DurationTo, DurationToLabel)
    }

    override fun onResume() {
        super.onResume()
        resumedP = true
        if (pendingBudgetLoad == 0L) setupListeners()
    }

    override fun onPause() {
        super.onPause()
        resumedP = false
    }

    private fun populateData(budget: Budget) {
        this.budget = budget
        Title.setText(budget.title)
        Description.setText(budget.description)
        with(accountSpinnerHelper) {
            (adapter as AccountAdapter).getPosition(budget.accountId).takeIf { it > -1 }?.let {
                setSelection(it)
            }
        }
        Amount.setFractionDigits(budget.currency.fractionDigits())
        Amount.setAmount(budget.amount.amountMajor)
        typeSpinnerHelper.setSelection(budget.grouping.ordinal)
        showDateRange(budget.grouping == Grouping.NONE)
        if (resumedP) setupListeners()
        pendingBudgetLoad = 0L
    }

    override fun onNothingSelected(parent: AdapterView<*>) {
       //noop
    }

    override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
        setDirty()
        when (parent.id) {
            R.id.Type -> showDateRange(position == Grouping.NONE.ordinal)
            R.id.Accounts -> Amount.setFractionDigits(currencyContext[selectedAccount().currency].fractionDigits())
        }
    }

    private fun showDateRange(visible: Boolean) {
        DurationFromRow.isVisible = visible
        DurationToRow.isVisible = visible
    }

    override fun onDateChanged(view: DatePicker?, year: Int, monthOfYear: Int, dayOfMonth: Int) {
        setDirty()
    }

    override fun dispatchCommand(command: Int, tag: Any?): Boolean {
        if (command == R.id.SAVE_COMMAND) {
            validateAmountInput(Amount, true)?.let { amount ->
                val grouping = typeSpinnerHelper.selectedItem as Grouping
                val start = if (grouping == Grouping.NONE) DurationFrom.getDate() else null
                val end = if (grouping == Grouping.NONE) DurationTo.getDate() else null
                if (end != null && start != null && end < start) {
                    showDismissableSnackbar(R.string.budget_date_end_after_start)
                } else {
                    val account: Account = selectedAccount()
                    val currencyUnit = currencyContext[account.currency]
                    val budget = Budget(budgetId, account.id,
                            Title.text.toString(), Description.text.toString(), currencyUnit,
                            Money(currencyUnit, amount),
                            grouping,
                            -1,
                            start,
                            end)
                    viewModel.saveBudget(budget)
                }

            }
            return true;
        }
        return super.dispatchCommand(command, tag)
    }

    private fun selectedAccount() = accountSpinnerHelper.selectedItem as Account
}

class GroupingAdapter(context: Context) : ArrayAdapter<Grouping>(context, android.R.layout.simple_spinner_item, android.R.id.text1, Grouping.values()) {

    init {
        setDropDownViewResource(R.layout.support_simple_spinner_dropdown_item)
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val row = super.getView(position, convertView, parent)
        setText(position, row)
        return row
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        val row = super.getDropDownView(position, convertView, parent)
        setText(position, row)
        return row
    }

    private fun setText(position: Int, row: View) {
        (row.findViewById<View>(android.R.id.text1) as TextView).setText(getItem(position)!!.getLabelForBudgetType())
    }
}

class AccountAdapter(context: Context, accounts: List<Account>) : ArrayAdapter<Account>(
        context, android.R.layout.simple_spinner_item, android.R.id.text1, accounts) {
    override fun hasStableIds(): Boolean = true
    override fun getItemId(position: Int): Long = getItem(position)!!.id
    fun getPosition(accountId: Long): Int {
        for (i in 0 until count) {
            if (getItem(i)!!.id == accountId) return i
        }
        return -1
    }
}

fun DatePicker.initWith(date: LocalDate, listener: DatePicker.OnDateChangedListener) {
    with(date) {
        init(year, monthValue - 1, dayOfMonth, listener)
    }
}

fun DatePicker.getDate() = LocalDate.of(year, month + 1, dayOfMonth)