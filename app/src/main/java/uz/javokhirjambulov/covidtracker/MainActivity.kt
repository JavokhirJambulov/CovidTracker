package uz.javokhirjambulov.covidtracker

import android.annotation.SuppressLint
import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.ColorLong
import androidx.core.content.ContextCompat
import com.google.gson.GsonBuilder
import com.robinhood.spark.SparkView
import org.angmarch.views.NiceSpinner
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

private const val BASE_URL="https://api.covidtracking.com/v1/"
private const val TAG = "MainActivity"
private const val ALL_SATES="All (Nationwide)"
class MainActivity : AppCompatActivity() {

    private lateinit var sparkView: SparkView
    private lateinit var currentlyShownData: List<CovidData>
    private lateinit var adapter: CovidSparkAdapter
    private lateinit var perStateDailyData: Map<String, List<CovidData>>
    private lateinit var nationalDailyData: List<CovidData>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val gson=GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ss").create()
        val retrofit=Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
        val covidService=retrofit.create(CovidService::class.java)

        //fetch the National Data
        covidService.getNationalData().enqueue(object :Callback<List<CovidData>>{
            override fun onResponse(
                call: Call<List<CovidData>>,
                response: Response<List<CovidData>>
            ) {
                Log.e(TAG,"onResponse $response")
                val nationalData= response.body()
                if(nationalData==null){
                    Log.w(TAG,"Did not receive a valid response body")
                    return 
                }
                setupEventListeners()
                nationalDailyData = nationalData.reversed()
                Log.i(TAG,"Update graph with national data")
                updateDisplayWithData(nationalDailyData)
            }

            override fun onFailure(call: Call<List<CovidData>>, t: Throwable) {

                Log.e(TAG,"onFailure $t")
            }

        })

        //fetch the State Data
        covidService.getStatesData().enqueue(object :Callback<List<CovidData>>{
            override fun onResponse(
                call: Call<List<CovidData>>,
                response: Response<List<CovidData>>
            ) {
                Log.e(TAG,"onResponse $response")
                val statesData= response.body()
                if(statesData==null){
                    Log.w(TAG,"Did not receive a valid response body")
                    return
                }
                perStateDailyData = statesData.reversed().groupBy { it.state }
                Log.i(TAG,"Update spinner with state names")
                updateSpinnerWithStateData(perStateDailyData.keys)
            }

            override fun onFailure(call: Call<List<CovidData>>, t: Throwable) {

                Log.e(TAG,"onFailure $t")
            }

        })

    }

    private fun updateSpinnerWithStateData(stateNames: Set<String>) {
        val stateAbbreviationList=stateNames.toMutableList()
        stateAbbreviationList.sort()
        stateAbbreviationList.add(0,ALL_SATES)
        //Add state list as the data source for the spinner
        val spinnerSelect= findViewById<NiceSpinner>(R.id.spinnerSelect)
        spinnerSelect.attachDataSource(stateAbbreviationList)
        spinnerSelect.setOnSpinnerItemSelectedListener { parent, _, position, _ ->
            val selectedState = parent.getItemAtPosition(position)as String
            val selectedData=perStateDailyData[selectedState]?:nationalDailyData
            updateDisplayWithData(selectedData)
        }

    }

    private fun setupEventListeners() {
        //Add a listener for the user scrubbing on the chart
        sparkView =findViewById<SparkView>(R.id.sparkView)
        sparkView.isScrubEnabled=true
        sparkView.setScrubListener { itemData->
            if(itemData is CovidData){
                updateInfoForDate(itemData)
            }
        }
        //Respond to radio button selected events
        val radioGroupTimeSelection =findViewById<RadioGroup>(R.id.radioGroupTimeSelection)
        radioGroupTimeSelection.setOnCheckedChangeListener { _, checkedId ->
            adapter.daysAgo= when(checkedId){
                R.id.radioButtonWeek->TimeScale.WEEK
                R.id.radioButtonMonth->TimeScale.MONTH
                else->TimeScale.MAX
            }
            adapter.notifyDataSetChanged()
        }
        val radioGroupMetricSelection =findViewById<RadioGroup>(R.id.radioGroupMetricSelection)
        radioGroupMetricSelection.setOnCheckedChangeListener { _, checkedId ->
            when(checkedId){
                R.id.radioButtonNegative->updateDisplayMetric(Metric.NEGATIVE)
                R.id.radioButtonPositive->updateDisplayMetric(Metric.POSTIVE)
                R.id.radioButtonDeath->updateDisplayMetric(Metric.DEATH)
            }
            adapter.notifyDataSetChanged()
        }
    }

    private fun updateDisplayMetric(metric: Metric) {
        //update color of the chart
        val colorRes=when(metric){
            Metric.NEGATIVE->R.color.colorNegative
            Metric.POSTIVE->R.color.colorPositive
            Metric.DEATH->R.color.colorDeath
        }
        @ColorInt val colorInt= ContextCompat.getColor(this, colorRes)
        sparkView.lineColor=colorInt
        val tvMetricLabel = findViewById<TextView>(R.id.tvMetricLabel)
        tvMetricLabel.setTextColor(colorInt)
        //update metric on the adapter
        adapter.metric=metric
        adapter.notifyDataSetChanged()
        //reset number and date shown in the bottom text views
        updateInfoForDate(currentlyShownData.last())
    }

    private fun updateDisplayWithData(dailyData: List<CovidData>) {
        currentlyShownData= dailyData
        //create a new sparkAdapter with the data
        adapter = CovidSparkAdapter(dailyData)
        sparkView =findViewById<SparkView>(R.id.sparkView)
        sparkView.adapter=adapter
        //update he radio buttons with the positive cases and max time selected
        val radioButtonPositive = findViewById<RadioButton>(R.id.radioButtonPositive)
        radioButtonPositive.isChecked=true
        val radioButtonMax = findViewById<RadioButton>(R.id.radioButtonMax)
        radioButtonMax.isChecked=true
        //display metric for the most recent data
        updateDisplayMetric(Metric.POSTIVE)
    }


    private fun updateInfoForDate(covidData: CovidData) {
        val numCases = when(adapter.metric){
            Metric.NEGATIVE->covidData.negativeIncrease
            Metric.POSTIVE->covidData.positiveIncrease
            Metric.DEATH->covidData.deathIncrease
        }
        val tvMetricLabel = findViewById<TextView>(R.id.tvMetricLabel)
        tvMetricLabel.text= NumberFormat.getInstance().format(numCases)
        val outputDataFormat=SimpleDateFormat("MMM dd, yyyy", Locale.US)
        val tvDateLabel = findViewById<TextView>(R.id.tvDateLabel)
        tvDateLabel.text=outputDataFormat.format(covidData.dateChecked)


    }
}