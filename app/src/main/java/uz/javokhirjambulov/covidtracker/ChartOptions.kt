package uz.javokhirjambulov.covidtracker

enum class Metric {
    NEGATIVE, POSTIVE, DEATH
}

enum class TimeScale(val numdays:Int) {
    WEEK(7),
    MONTH(30),
    MAX(-1)

}