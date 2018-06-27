
setwd("D:\\Projects_BigData\\__Profiling_StatsD\\Projects\\statsd-jvm-profiler\\visualization\\output")
filename <- "memory_jvm_2015_07_28_12_07_26_162__vin-h5.poc-vin.cloud.edmunds.com_30142.txt"

# args <- commandArgs(trailingOnly = TRUE)
# filename <- args[1]

df <- read.csv(filename, sep = '\t', header = FALSE)
colnames(df) <- c("group", "measure", "time", "value")
df$time <- as.POSIXlt(df$time)
# str(df)

library(ggplot2)
library(scales)

normalFormat <- function(l) {
  format(l, scientific = FALSE, big.mark=' ')
}

output <- function(d, caption, filename, xsize, ysize){
  png(height=xsize, width=ysize, file=filename)
  p <- ggplot(data=d) + geom_line(aes(x=time, y=value, colour=measure), size=1.3) +
          ggtitle(caption) +
          expand_limits(y=0) +
          scale_x_datetime(labels=date_format("%d-%m-%Y %H:%M:%S")) + 
          scale_y_continuous(labels = normalFormat) +
          theme(axis.text.x = element_text(angle = 45, hjust = 1))  
  print(p)
  dev.off()
}

filters <- c('Heap', 'Non Heap', 'Pending Finalization Count', 'GC', 'Class Count')
for (filter in filters){
  newfilename <- paste(paste(paste(filename, '_', sep = ""), filter, sep=""), '.png', sep="")
  newfilename
  d <- subset(df, df$group==filter)
  output(d, filter, newfilename, 700, 700)
}