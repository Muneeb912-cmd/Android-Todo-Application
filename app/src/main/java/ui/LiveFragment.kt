import Adapters.RecyclerViewAdapter
import DataClass.DataClass
import Models.CardDataRepository
import ViewModels.CardDataViewModel
import ViewModels.CardDataViewModelFactory
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TimePicker
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.week3_challenge.*
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class LiveFragment : Fragment(), RecyclerViewAdapter.OnItemClickListener {

    private lateinit var cardViewModel: CardDataViewModel
    private lateinit var adapter: RecyclerViewAdapter
    private lateinit var recyclerView: RecyclerView
    private var dataList = ArrayList<DataClass>()
    private var selectedImageUri: Uri? = null
    private lateinit var changeImage: ActivityResultLauncher<Intent>
    private var currentEditImage: ImageView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = CardDataRepository()
        val factory = CardDataViewModelFactory(repository)
        cardViewModel = ViewModelProvider(this, factory)[CardDataViewModel::class.java]

    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_live, container, false)
        setUpRecyclerView(view)

        val repository = CardDataRepository()
        val factory = CardDataViewModelFactory(repository)
        cardViewModel = ViewModelProvider(this, factory)[CardDataViewModel::class.java]

        // Observe LiveData
        cardViewModel.cardData.observe(viewLifecycleOwner, Observer { data ->
            if (data.isNotEmpty()) {
                data?.let {
                    dataList.clear()
                    dataList.addAll(it)
                    adapter.notifyDataSetChanged()
                }
            }
        })

        changeImage = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val data = result.data
                val imgUri = data?.data
                selectedImageUri = imgUri
                currentEditImage?.setImageURI(imgUri)
                // You might want to store imgUri somewhere for later use
            }
        }

        // Set up ItemTouchHelper for swipe functionality
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                cardViewModel.deleteTask(dataList[position].id)
                recyclerView.adapter?.notifyItemRemoved(position)
                Snackbar.make(recyclerView, "Deleted " + dataList[position].cardTitle +" Successfully!", Snackbar.LENGTH_LONG).show()
            }
        })
        itemTouchHelper.attachToRecyclerView(recyclerView)

        return view
    }

    private fun setUpRecyclerView(view: View) {
        recyclerView = view.findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.setHasFixedSize(true)
        adapter = RecyclerViewAdapter(dataList, this)
        recyclerView.adapter = adapter
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Defining custom Spinner
        val mySpinner = view.findViewById<Spinner>(R.id.spinner)
        val spinnerList = arrayOf("Today", "Tomorrow", "Yesterday")
        val mArrayAdapter = ArrayAdapter(requireContext(), R.layout.spinner_item, spinnerList)
        mArrayAdapter.setDropDownViewResource(R.layout.spinner_item)
        mySpinner.adapter = mArrayAdapter

        // Populating tab layout
        val tabsList = timeList()
        val tabLayout = view.findViewById<TabLayout>(R.id.tabLayout2)
        for (time in tabsList) {
            val tab = tabLayout.newTab()
            tab.text = time
            tabLayout.addTab(tab)
        }

        // Observe filtered data
        cardViewModel.filteredCardData.observe(viewLifecycleOwner, Observer { data ->
            dataList.clear()
            if (data != null) {
                dataList.addAll(data)
            }
            adapter.notifyDataSetChanged()
        })

        // Setting up tab selection listener
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            @RequiresApi(Build.VERSION_CODES.O)
            override fun onTabSelected(tab: TabLayout.Tab?) {
                tab?.let {
                    val selectedTabText = it.text.toString()
                    cardViewModel.filterDataByTime(selectedTabText)
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {
                // Optional: handle tab unselected event if needed
            }

            override fun onTabReselected(tab: TabLayout.Tab?) {
                // Optional: handle tab reselected event if needed
            }
        })
    }


    private fun timeList(): List<String> {
        val tabsList = listOf(
            "01:00 AM", "02:00 AM", "03:00 AM", "04:00 AM", "05:00 AM",
            "06:00 AM", "07:00 AM", "08:00 AM", "09:00 AM",
            "10:00 AM", "11:00 AM", "12:00 PM", "01:00 PM",
            "02:00 PM", "03:00 PM", "04:00 PM", "05:00 PM",
            "06:00 PM", "07:00 PM", "08:00 PM", "09:00 PM",
            "10:00 PM", "11:00 PM", "12:00 PM"
        )
        return tabsList
    }

    override fun onItemClick(position: Int) {
        val clickedItem = dataList[position]
        val alertDialogBuilder = AlertDialog.Builder(requireContext())
            .setTitle("Click Action")
            .setMessage("Click Action happened on: ${clickedItem.cardTitle}")
            .setIcon(R.drawable.ic_info)
            .setPositiveButton("Ok") { dialog, _ ->
                Toast.makeText(requireContext(), "Okay Clicked", Toast.LENGTH_SHORT).show()
            }
        alertDialogBuilder.show()
    }

    override fun onButtonClick(position: Int) {
        val clickedItem = dataList[position]
        val alertDialogBuilder = alertDialogBuilder(clickedItem)


        // Inflate custom layout for EditText
        val layoutInflater = LayoutInflater.from(requireContext())
        val dialogView = layoutInflater.inflate(R.layout.edit_card_data, null)
        alertDialogBuilder.setView(dialogView)

        // Access the EditText fields in the dialog layout
        val editCardTitle = dialogView.findViewById<EditText>(R.id.editCardTitle)
        val editStartTime = dialogView.findViewById<TimePicker>(R.id.editStartTime)
        val editEndTime = dialogView.findViewById<TimePicker>(R.id.editEndTime)
        val editImgCaption = dialogView.findViewById<EditText>(R.id.editImgCaption)
        val editImage = dialogView.findViewById<ImageView>(R.id.editImage)
        val storageReference: StorageReference =
            FirebaseStorage.getInstance().getReferenceFromUrl(clickedItem.imgId)
        val uploadBtn = dialogView.findViewById<Button>(R.id.uploadPic)

        // Get the download URL
        storageReference.downloadUrl.addOnSuccessListener { uri ->
            selectedImageUri=uri
            Glide.with(editImage.context)
                .load(uri.toString())
                .into(editImage)
        }.addOnFailureListener {
            editImage.setImageResource(R.drawable.ic_photo) // Set a placeholder image or handle error
        }
//        currentEditImage = editImage
        populateData(
            editCardTitle,
            clickedItem,
            editStartTime,
            editEndTime,
            editImgCaption,
            editImage,
        )

        // Set positive button (Update)
        alertDialogBuilder.setPositiveButton("Update") { dialog, _ ->
            onUpdateClick(
                editCardTitle,
                editStartTime,
                editEndTime,
                editImgCaption,
                editImage,
                clickedItem
            )
        }

        // Set negative button (Cancel)
        alertDialogBuilder.setNegativeButton("Cancel") { dialog, _ ->
            // Cancel action, do nothing or dismiss dialog
            dialog.dismiss()
        }

        uploadBtn.setOnClickListener {
            currentEditImage = editImage // Set the ImageView where the image will be displayed
            val pickImg = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.INTERNAL_CONTENT_URI)
            changeImage.launch(pickImg)
        }

        // Show the AlertDialog
        alertDialogBuilder.show()
    }

    private fun populateData(
        editCardTitle: EditText,
        clickedItem: DataClass,
        editStartTime: TimePicker,
        editEndTime: TimePicker,
        editImgCaption: EditText,
        editImage: ImageView
    ) {
        // Populate EditText fields with current data
        editCardTitle.setText(clickedItem.cardTitle)
        editImgCaption.setText(clickedItem.imgCaption)
        editImage.setImageURI(clickedItem.imgId.toUri())

        // Function to set the time on the TimePicker
        fun setTimePicker(timePicker: TimePicker, time: String) {
            val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
            val calendar = Calendar.getInstance()
            calendar.time = sdf.parse(time) ?: return

            timePicker.hour = calendar.get(Calendar.HOUR_OF_DAY)
            timePicker.minute = calendar.get(Calendar.MINUTE)
        }

        // Set the start and end times on the TimePickers
        setTimePicker(editStartTime, clickedItem.startTime)
        setTimePicker(editEndTime, clickedItem.endTime)
    }

    private fun onUpdateClick(
        editCardTitle: EditText,
        editStartTime: TimePicker,
        editEndTime: TimePicker,
        editImgCaption: EditText,
        editImage: ImageView,
        clickedItem: DataClass
    ) {
        // Function to format time
        fun formatTime(hour: Int, minute: Int): String {
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
            }
            val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault()) // 12-hour format with AM/PM
            return sdf.format(calendar.time)
        }

        // Extract the time from TimePicker and format it
        val startTimeFormatted = formatTime(editStartTime.hour, editStartTime.minute)
        val endTimeFormatted = formatTime(editEndTime.hour, editEndTime.minute)

        val title = editCardTitle.text.toString()
        val imgCaption = editImgCaption.text.toString()
        val id=clickedItem.id

        val dataClass: DataClass = DataClass(
            imgCaption = imgCaption,
            imgId = selectedImageUri.toString(),
            id = id,
            startTime = startTimeFormatted,
            cardTitle = title,
            endTime = endTimeFormatted,
        )

        cardViewModel.updateTask(
            cardData = dataClass,
            imageUri = selectedImageUri as Uri
        )
        Log.d("Value", "onUpdateClick: $id")

    }

    private fun alertDialogBuilder(clickedItem: DataClass): AlertDialog.Builder {
        // Build the AlertDialog
        val alertDialogBuilder = AlertDialog.Builder(requireContext())
        alertDialogBuilder.setTitle("Update ${clickedItem.cardTitle}")
        alertDialogBuilder.setIcon(R.drawable.ic_info)
        return alertDialogBuilder
    }

}
