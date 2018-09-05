package org.simple.clinic.home.overdue

import android.support.v4.content.ContextCompat
import android.support.v7.recyclerview.extensions.ListAdapter
import android.support.v7.util.DiffUtil
import android.support.v7.widget.RecyclerView
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import io.reactivex.subjects.PublishSubject
import kotterknife.bindView
import org.simple.clinic.R
import org.simple.clinic.patient.Gender
import org.simple.clinic.util.Truss
import java.util.UUID

class OverdueListAdapter(val phoneCallClickStream: PublishSubject<CallPatientClicked>) : ListAdapter<OverdueListItem, OverdueListViewHolder>(OverdueListDiffer()) {

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OverdueListViewHolder {
    val layout = LayoutInflater.from(parent.context).inflate(R.layout.item_overdue_list, parent, false)
    return OverdueListViewHolder(layout, phoneCallClickStream)
  }

  override fun onBindViewHolder(holder: OverdueListViewHolder, position: Int) {
    holder.appointment = getItem(position)
    holder.render()
  }
}

data class OverdueListItem(
    val appointmentUuid: UUID,
    val name: String,
    val gender: Gender,
    val age: Int,
    val phoneNumber: String? = null,
    val bpSystolic: Int,
    val bpDiastolic: Int,
    val bpDaysAgo: Int,
    val overdueDays: Int
)

class OverdueListViewHolder(
    itemView: View,
    private val phoneCallClickStream: PublishSubject<CallPatientClicked>
) : RecyclerView.ViewHolder(itemView) {

  private val patientName by bindView<TextView>(R.id.overdue_patient_name_gender_age)
  private val patientBP by bindView<TextView>(R.id.overdue_patient_bp)
  private val overdueDays by bindView<TextView>(R.id.overdue_days)
  private val callButton by bindView<ImageButton>(R.id.overdue_patient_call)
  private val separator by bindView<View>(R.id.overdue_separator)

  lateinit var appointment: OverdueListItem

  init {
    callButton.setOnClickListener {
      phoneCallClickStream.onNext(CallPatientClicked(appointment.phoneNumber!!))
    }
  }

  fun render() {
    val context = itemView.context
    val spanBuilder = Truss()
        .append(appointment.name)
        .append(" ")
        .pushSpan(ForegroundColorSpan(ContextCompat.getColor(context, R.color.black_opacity_50)))
        .pushSpan(AbsoluteSizeSpan(14, true))
        .append(context.getString(R.string.overdue_list_item_patient_gender_age, context.getString(appointment.gender.displayTextRes), appointment.age))
        .popSpan()

    patientName.text = spanBuilder.build()
    patientBP.text = context.resources.getQuantityString(R.plurals.overdue_list_item_patient_bp, appointment.bpDaysAgo, appointment.bpDaysAgo, appointment.bpSystolic, appointment.bpDiastolic)
    overdueDays.text = context.resources.getQuantityString(R.plurals.overdue_list_item_overdue_days, appointment.overdueDays, appointment.overdueDays)

    if (appointment.phoneNumber == null) {
      callButton.visibility = View.GONE
      separator.visibility = View.GONE
    } else {
      callButton.visibility = View.VISIBLE
      separator.visibility = View.VISIBLE
    }
  }
}

class OverdueListDiffer : DiffUtil.ItemCallback<OverdueListItem>() {

  override fun areItemsTheSame(oldItem: OverdueListItem, newItem: OverdueListItem): Boolean = oldItem.appointmentUuid == newItem.appointmentUuid

  override fun areContentsTheSame(oldItem: OverdueListItem, newItem: OverdueListItem): Boolean = oldItem == newItem
}
